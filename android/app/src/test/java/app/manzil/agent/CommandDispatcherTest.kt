package app.manzil.agent

import app.manzil.model.JourneyProgress
import app.manzil.model.ProgressStatus
import app.manzil.model.SampleJourneys
import app.manzil.model.SavedJourney
import app.manzil.model.StoredState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class CommandDispatcherTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val journey = SampleJourneys.chennaiCentral
    private val savedAt = SampleJourneys.contentPreparedAtEpochMs + 60 * 60_000L // 1 pm IST, 5 Nov
    private val now = savedAt + 2 * 60 * 60_000L // 3 pm IST

    private fun state(index: Int, status: ProgressStatus = ProgressStatus.ACTIVE, version: Long = 7) = StoredState(
        journeys = listOf(SavedJourney(journey, savedAt)),
        progress = JourneyProgress(journey.id, index, status, savedAt, savedAt, version),
    )

    private fun run(command: LocalCommand, state: StoredState) =
        CommandDispatcher.dispatch(command, DispatchContext(state, now, zone))

    @Test
    fun nextAdvancesAndBumpsVersion() {
        val result = run(LocalCommand.NextStep, state(0))
        assertTrue(result.outcome.success)
        assertEquals(1, result.newProgress!!.stepIndex)
        assertEquals(8L, result.newProgress!!.stateVersion)
        assertEquals(8L, result.outcome.stateVersion)
        assertTrue(result.outcome.message.startsWith("Step 2 of 5: Board a train towards Chennai Beach."))
        assertTrue(result.outcome.effect is CommandEffect.Speak)
    }

    @Test
    fun nextOnLastStepFailsWithoutChangingProgress() {
        val result = run(LocalCommand.NextStep, state(journey.steps.lastIndex))
        assertFalse(result.outcome.success)
        assertNull(result.newProgress)
        assertEquals(7L, result.outcome.stateVersion)
    }

    @Test
    fun previousOnFirstStepFails() {
        val result = run(LocalCommand.PreviousStep, state(0))
        assertFalse(result.outcome.success)
        assertNull(result.newProgress)
    }

    @Test
    fun goToStepValidatesRange() {
        assertFalse(run(LocalCommand.GoToStep(0), state(0)).outcome.success)
        assertFalse(run(LocalCommand.GoToStep(6), state(0)).outcome.success)
        val ok = run(LocalCommand.GoToStep(4), state(0))
        assertTrue(ok.outcome.success)
        assertEquals(3, ok.newProgress!!.stepIndex)
    }

    @Test
    fun selectingAStepResumesAFinishedJourney() {
        val result = run(LocalCommand.GoToStep(2), state(4, ProgressStatus.FINISHED))
        assertEquals(ProgressStatus.ACTIVE, result.newProgress!!.status)
        assertEquals(1, result.newProgress!!.stepIndex)
    }

    @Test
    fun repeatIncludesCautionAndDoesNotChangeProgress() {
        val result = run(LocalCommand.RepeatInstruction, state(1))
        assertTrue(result.outcome.success)
        assertNull(result.newProgress)
        assertTrue(result.outcome.message.contains("Note: Service times and platforms are unverified."))
    }

    @Test
    fun explainIsGroundedInSavedText() {
        val result = run(LocalCommand.ExplainSavedStep, state(2))
        assertTrue(result.outcome.message.startsWith(journey.steps[2].explanation))
        assertTrue(result.outcome.message.contains("saved text from today at 1:00 pm"))
    }

    @Test
    fun dataAgeStatesSampleProvenanceAndNoLiveUpdates() {
        val message = run(LocalCommand.ShowDataAge, state(0)).outcome.message
        assertEquals(
            "Saved to this phone 2 h ago, today at 1:00 pm. The content is sample data written 5 Nov 2026, " +
                "not verified transit directions. No live updates have been received.",
            message,
        )
    }

    @Test
    fun liveQuestionsAreRefusedHonestly() {
        val result = run(LocalCommand.LiveInfoUnavailable, state(0))
        assertFalse(result.outcome.success)
        assertTrue(result.outcome.message.contains("don't have live traffic"))
        assertNull(result.newProgress)
    }

    @Test
    fun endJourneyFinishesOnce() {
        val first = run(LocalCommand.EndJourney, state(2))
        assertEquals(ProgressStatus.FINISHED, first.newProgress!!.status)
        val again = run(LocalCommand.EndJourney, state(2, ProgressStatus.FINISHED))
        assertTrue(again.outcome.success)
        assertNull(again.newProgress)
        assertFalse(run(LocalCommand.NextStep, state(2, ProgressStatus.FINISHED)).outcome.success)
    }

    @Test
    fun openMapsRequestsDocumentedHandOffOnly() {
        val result = run(LocalCommand.OpenGoogleMapsDestination, state(0))
        val effect = result.outcome.effect as CommandEffect.OpenMapsDestination
        assertEquals(journey.destination, effect.destination)
        assertTrue(result.outcome.message.contains("Manzil can't see it"))
    }

    @Test
    fun commandsWithoutAJourneyFail() {
        val result = run(LocalCommand.NextStep, StoredState.EMPTY)
        assertFalse(result.outcome.success)
        assertEquals(0L, result.outcome.stateVersion)
        assertTrue(run(LocalCommand.Help, StoredState.EMPTY).outcome.success)
    }

    @Test
    fun unsupportedListsCapabilities() {
        val result = run(LocalCommand.Unsupported(LocalCommand.Unsupported.Reason.NOT_UNDERSTOOD), state(0))
        assertFalse(result.outcome.success)
        assertEquals("unsupported", result.outcome.tool)
        assertTrue(result.outcome.message.contains(CommandDispatcher.CAPABILITIES))
    }
}
