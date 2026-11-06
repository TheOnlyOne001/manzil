package app.manzil.data

import app.manzil.agent.JourneyController
import app.manzil.agent.LocalCommand
import app.manzil.model.LoadIssue
import app.manzil.model.ProgressStatus
import app.manzil.model.SampleJourneys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** In-memory stand-in for SharedPreferences with the same atomic-write contract. */
class InMemoryStore(var failWrites: Boolean = false) : KeyValueStore {
    val data = mutableMapOf<String, String>()

    override fun read(key: String): String? = data[key]

    override fun write(changes: Map<String, String?>): Boolean {
        if (failWrites) return false
        changes.forEach { (k, v) -> if (v == null) data.remove(k) else data[k] = v }
        return true
    }
}

class JourneyRepositoryTest {
    private val journey = SampleJourneys.chennaiCentral
    private var clock = 1_800_000_000_000L

    private fun controller(store: KeyValueStore) = JourneyController(
        repository = JourneyRepository(store),
        clock = { clock },
        zone = { ZoneId.of("Asia/Kolkata") },
        io = Dispatchers.Unconfined,
    )

    @Test
    fun emptyStoreLoadsEmptyState() {
        val state = JourneyRepository(InMemoryStore()).ensureLoaded()
        assertTrue(state.journeys.isEmpty())
        assertNull(state.progress)
        assertNull(state.loadIssue)
    }

    @Test
    fun codecRoundTripIsLossless() {
        val store = InMemoryStore()
        val repo = JourneyRepository(store)
        assertTrue(repo.saveJourney(journey, clock))
        val decoded = JourneyCodec.decode(store.data.getValue(JourneyRepository.KEY_STATE))
        assertEquals(repo.state.value, decoded)
        assertEquals(journey, decoded.journeys.single().journey)
    }

    @Test
    fun progressSurvivesProcessDeath() = runBlocking {
        val store = InMemoryStore()
        val first = controller(store)
        assertTrue(first.saveJourney(journey))
        assertTrue(first.start(journey.id).success)
        clock += 60_000
        assertTrue(first.execute(LocalCommand.NextStep).success)
        assertTrue(first.execute(LocalCommand.NextStep).success)

        // "Kill" the process: brand-new repository and controller over the same durable store.
        val revived = controller(store)
        val state = revived.load()
        val progress = assertNotNullAndGet(state.progress)
        assertEquals(journey.id, progress.journeyId)
        assertEquals(2, progress.stepIndex)
        assertEquals(ProgressStatus.ACTIVE, progress.status)
        assertEquals(3L, progress.stateVersion)
        assertTrue(state.hasJourneyInProgress)

        val repeat = revived.execute(LocalCommand.RepeatInstruction)
        assertTrue(repeat.message.startsWith("Step 3 of 5"))
    }

    @Test
    fun finishIsDurable() = runBlocking {
        val store = InMemoryStore()
        val c = controller(store)
        c.saveJourney(journey)
        c.start(journey.id)
        assertTrue(c.execute(LocalCommand.EndJourney).success)
        val revived = controller(store).load()
        assertEquals(ProgressStatus.FINISHED, revived.progress?.status)
        assertFalse(revived.hasJourneyInProgress)
    }

    @Test
    fun failedWriteReportsFailureAndKeepsState() = runBlocking {
        val store = InMemoryStore()
        val c = controller(store)
        c.saveJourney(journey)
        c.start(journey.id)
        store.failWrites = true
        val outcome = c.execute(LocalCommand.NextStep)
        assertFalse(outcome.success)
        assertEquals(JourneyController.PERSIST_FAILED, outcome.message)
        assertEquals(0, c.state.value.progress?.stepIndex)
    }

    @Test
    fun corruptDataIsBackedUpAndReset() {
        val store = InMemoryStore()
        store.data[JourneyRepository.KEY_STATE] = "{not json"
        val repo = JourneyRepository(store)
        val state = repo.ensureLoaded()
        assertEquals(LoadIssue.CORRUPT_RESET, state.loadIssue)
        assertEquals("{not json", store.data[JourneyRepository.KEY_CORRUPT_BACKUP])
        assertNull(store.data[JourneyRepository.KEY_STATE])
        assertTrue(repo.saveJourney(journey, clock))
        assertNull(repo.state.value.loadIssue)
    }

    @Test
    fun newerSchemaIsNeverOverwritten() {
        val store = InMemoryStore()
        val raw = """{"schemaVersion":99,"journeys":[],"progress":null}"""
        store.data[JourneyRepository.KEY_STATE] = raw
        val repo = JourneyRepository(store)
        assertEquals(LoadIssue.NEWER_SCHEMA, repo.ensureLoaded().loadIssue)
        assertFalse(repo.saveJourney(journey, clock))
        assertEquals(raw, store.data[JourneyRepository.KEY_STATE])
    }

    @Test
    fun removingAJourneyDropsItsProgress() = runBlocking {
        val store = InMemoryStore()
        val c = controller(store)
        c.saveJourney(journey)
        c.start(journey.id)
        assertTrue(c.removeJourney(journey.id))
        val revived = controller(store).load()
        assertTrue(revived.journeys.isEmpty())
        assertNull(revived.progress)
    }

    @Test
    fun progressPointingOutOfRangeIsDropped() {
        val store = InMemoryStore()
        val repo = JourneyRepository(store)
        repo.saveJourney(journey, clock)
        val raw = store.data.getValue(JourneyRepository.KEY_STATE)
            .replace("\"progress\":null", "\"progress\":{\"journeyId\":\"${journey.id}\",\"stepIndex\":99,\"status\":\"ACTIVE\",\"startedAt\":1,\"updatedAt\":1,\"stateVersion\":1}")
        val decoded = JourneyCodec.decode(raw)
        assertNull(decoded.progress)
    }

    private fun <T> assertNotNullAndGet(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}
