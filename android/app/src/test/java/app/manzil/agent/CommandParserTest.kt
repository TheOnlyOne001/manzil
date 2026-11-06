package app.manzil.agent

import app.manzil.agent.LocalCommand.Unsupported.Reason
import org.junit.Assert.assertEquals
import org.junit.Test

class CommandParserTest {
    private fun parse(text: String) = CommandParser.parse(text)

    @Test
    fun basicNavigationCommands() {
        assertEquals(LocalCommand.NextStep, parse("Next"))
        assertEquals(LocalCommand.NextStep, parse("  what's next?  "))
        assertEquals(LocalCommand.NextStep, parse("next stop"))
        assertEquals(LocalCommand.PreviousStep, parse("go back"))
        assertEquals(LocalCommand.PreviousStep, parse("undo"))
        assertEquals(LocalCommand.RepeatInstruction, parse("repeat"))
        assertEquals(LocalCommand.RepeatInstruction, parse("Say that again"))
        assertEquals(LocalCommand.ShowSavedJourney, parse("show all steps"))
    }

    @Test
    fun stepSelectionUnderstandsDigitsWordsAndOrdinals() {
        assertEquals(LocalCommand.GoToStep(3), parse("go to step 3"))
        assertEquals(LocalCommand.GoToStep(3), parse("step three"))
        assertEquals(LocalCommand.GoToStep(3), parse("I'm at the third step"))
        assertEquals(LocalCommand.GoToStep(2), parse("back to step 2"))
        // Range is validated by the dispatcher, not the parser.
        assertEquals(LocalCommand.GoToStep(0), parse("step 0"))
        assertEquals(LocalCommand.GoToStep(42), parse("step 42"))
    }

    @Test
    fun explainAndDataAgeTakePrecedenceOverNavigationWords() {
        assertEquals(LocalCommand.ExplainSavedStep, parse("explain the next step"))
        assertEquals(LocalCommand.ExplainSavedStep, parse("why?"))
        assertEquals(LocalCommand.ShowDataAge, parse("How old is this?"))
        assertEquals(LocalCommand.ShowDataAge, parse("is this up to date"))
    }

    @Test
    fun mapsEndAndStopSpeaking() {
        assertEquals(LocalCommand.OpenGoogleMapsDestination, parse("open google maps"))
        assertEquals(LocalCommand.EndJourney, parse("finish journey"))
        assertEquals(LocalCommand.EndJourney, parse("I have arrived"))
        assertEquals(LocalCommand.StopSpeaking, parse("stop"))
        assertEquals(LocalCommand.StopSpeaking, parse("stop talking"))
    }

    @Test
    fun liveQuestionsAreExplicitlyUnavailable() {
        assertEquals(LocalCommand.LiveInfoUnavailable, parse("when is the next train?"))
        assertEquals(LocalCommand.LiveInfoUnavailable, parse("is there traffic"))
        assertEquals(LocalCommand.LiveInfoUnavailable, parse("is the train delayed"))
        assertEquals(LocalCommand.LiveInfoUnavailable, parse("which platform"))
    }

    @Test
    fun unsupportedInputNeverBecomesAnAction() {
        assertEquals(LocalCommand.Unsupported(Reason.EMPTY), parse("   "))
        assertEquals(LocalCommand.Unsupported(Reason.TOO_LONG), parse("a".repeat(CommandParser.MAX_INPUT_CHARS + 1)))
        assertEquals(LocalCommand.Unsupported(Reason.NOT_UNDERSTOOD), parse("delete all files"))
        assertEquals(LocalCommand.Unsupported(Reason.NOT_UNDERSTOOD), parse("send a message to mom"))
        assertEquals(LocalCommand.Unsupported(Reason.NOT_UNDERSTOOD), parse("rm -rf /"))
        assertEquals(LocalCommand.Unsupported(Reason.NOT_UNDERSTOOD), parse("அடுத்து"))
        assertEquals(LocalCommand.Unsupported(Reason.NOT_UNDERSTOOD), parse("?!"))
        assertEquals(LocalCommand.Unsupported(Reason.LOCATION_NOT_USED), parse("where am I"))
        assertEquals(LocalCommand.Unsupported(Reason.EMERGENCY), parse("call an ambulance"))
    }

    @Test
    fun helpIsAnswered() {
        assertEquals(LocalCommand.Help, parse("help"))
        assertEquals(LocalCommand.Help, parse("what can you do?"))
    }
}
