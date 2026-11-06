package app.manzil.agent

import app.manzil.data.JourneyRepository
import app.manzil.model.Journey
import app.manzil.model.JourneyProgress
import app.manzil.model.ProgressStatus
import app.manzil.model.StoredState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * Single entry point used by both the app UI and the overlay bubble. Commands are serialised,
 * executed against the saved state, persisted, and only then reported. A command whose progress
 * could not be written reports failure rather than a fabricated success.
 */
class JourneyController(
    private val repository: JourneyRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()

    val state: StateFlow<StoredState> get() = repository.state

    suspend fun load(): StoredState = withContext(io) { repository.ensureLoaded() }

    suspend fun saveJourney(journey: Journey): Boolean =
        mutex.withLock { withContext(io) { repository.saveJourney(journey, clock()) } }

    suspend fun removeJourney(journeyId: String): Boolean =
        mutex.withLock { withContext(io) { repository.removeJourney(journeyId) } }

    /** Resume an active journey, or (re)start it from step 1. */
    suspend fun start(journeyId: String): CommandOutcome =
        mutex.withLock { withContext(io) { startBlocking(journeyId) } }

    suspend fun execute(command: LocalCommand): CommandOutcome =
        mutex.withLock { withContext(io) { executeBlocking(command) } }

    private fun startBlocking(journeyId: String): CommandOutcome {
        val state = repository.ensureLoaded()
        val saved = state.find(journeyId)
            ?: return CommandOutcome("start_journey", false, "That saved journey is no longer on this phone.", state.progress?.stateVersion ?: 0)
        val current = state.progress
        if (current != null && current.journeyId == journeyId && current.status == ProgressStatus.ACTIVE) {
            return CommandOutcome(
                "start_journey",
                true,
                CommandDispatcher.describeStep(saved.journey, current.stepIndex),
                current.stateVersion,
            )
        }
        val now = clock()
        val progress = JourneyProgress(
            journeyId = journeyId,
            stepIndex = 0,
            status = ProgressStatus.ACTIVE,
            startedAtEpochMs = now,
            updatedAtEpochMs = now,
            stateVersion = (current?.stateVersion ?: 0L) + 1,
        )
        if (!repository.writeProgress(progress)) {
            return CommandOutcome("start_journey", false, PERSIST_FAILED, current?.stateVersion ?: 0)
        }
        return CommandOutcome("start_journey", true, CommandDispatcher.describeStep(saved.journey, 0), progress.stateVersion)
    }

    private fun executeBlocking(command: LocalCommand): CommandOutcome {
        val state = repository.ensureLoaded()
        val result = CommandDispatcher.dispatch(command, DispatchContext(state, clock(), zone()))
        val newProgress = result.newProgress
        if (newProgress != null && newProgress != state.progress) {
            if (!repository.writeProgress(newProgress)) {
                return CommandOutcome(command.toolName, false, PERSIST_FAILED, state.progress?.stateVersion ?: 0)
            }
        }
        return result.outcome
    }

    companion object {
        const val PERSIST_FAILED = "Couldn't save progress on this phone, so nothing changed. Please try again."
    }
}
