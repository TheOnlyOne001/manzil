package app.manzil.data

import app.manzil.model.Journey
import app.manzil.model.JourneyProgress
import app.manzil.model.LoadIssue
import app.manzil.model.SavedJourney
import app.manzil.model.StoredState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local source of truth for saved journeys and progress. Every mutation is written synchronously
 * as one JSON document before the in-memory [state] changes, so observers never see progress
 * that was not persisted. Blocking: call from a background dispatcher.
 */
class JourneyRepository(private val store: KeyValueStore) {
    private val lock = Any()
    private val _state = MutableStateFlow(StoredState.EMPTY)
    val state: StateFlow<StoredState> = _state.asStateFlow()

    @Volatile
    private var loaded = false

    fun ensureLoaded(): StoredState = synchronized(lock) {
        if (!loaded) loadLocked()
        _state.value
    }

    private fun loadLocked() {
        val raw = store.read(KEY_STATE)
        val result = if (raw == null) {
            StoredState.EMPTY
        } else {
            try {
                JourneyCodec.decode(raw)
            } catch (e: NewerSchemaException) {
                StoredState(emptyList(), null, LoadIssue.NEWER_SCHEMA)
            } catch (e: Exception) {
                // Keep the unreadable payload for diagnosis, then start clean.
                store.write(mapOf(KEY_CORRUPT_BACKUP to raw, KEY_STATE to null))
                StoredState(emptyList(), null, LoadIssue.CORRUPT_RESET)
            }
        }
        _state.value = result
        loaded = true
    }

    /** Returns false (and leaves state untouched) if the change could not be persisted. */
    fun update(transform: (StoredState) -> StoredState): Boolean = synchronized(lock) {
        if (!loaded) loadLocked()
        val current = _state.value
        if (current.loadIssue == LoadIssue.NEWER_SCHEMA) return@synchronized false
        val next = transform(current).copy(loadIssue = null)
        val ok = store.write(mapOf(KEY_STATE to JourneyCodec.encode(next)))
        if (ok) _state.value = next
        ok
    }

    fun saveJourney(journey: Journey, savedAtEpochMs: Long): Boolean = update { state ->
        val others = state.journeys.filterNot { it.journey.id == journey.id }
        state.copy(journeys = others + SavedJourney(journey, savedAtEpochMs))
    }

    fun removeJourney(journeyId: String): Boolean = update { state ->
        state.copy(
            journeys = state.journeys.filterNot { it.journey.id == journeyId },
            progress = state.progress?.takeIf { it.journeyId != journeyId },
        )
    }

    fun writeProgress(progress: JourneyProgress?): Boolean = update { it.copy(progress = progress) }

    companion object {
        const val KEY_STATE = "manzil.journeys.v1"
        const val KEY_CORRUPT_BACKUP = "manzil.journeys.v1.corrupt"
    }
}
