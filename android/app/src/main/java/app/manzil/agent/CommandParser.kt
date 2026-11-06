package app.manzil.agent

import app.manzil.agent.LocalCommand.Unsupported.Reason
import java.util.Locale

/**
 * Deterministic keyword parser for typed commands. Not a language model: it matches a small set
 * of English phrases (whole words only) and returns [LocalCommand.Unsupported] for everything
 * else, including any non-English text. Order of checks matters and is covered by tests.
 */
object CommandParser {
    const val MAX_INPUT_CHARS = 160

    private val numberWords = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
    )
    private val ordinalWords = mapOf(
        "first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5,
        "sixth" to 6, "seventh" to 7, "eighth" to 8, "ninth" to 9, "tenth" to 10,
        "1st" to 1, "2nd" to 2, "3rd" to 3, "4th" to 4, "5th" to 5,
        "6th" to 6, "7th" to 7, "8th" to 8, "9th" to 9, "10th" to 10,
    )

    private val emergency = listOf("emergency", "ambulance", "police", "sos", "accident", "injured", "fire")
    private val help = listOf("help", "what can you do", "commands", "options")
    private val location = listOf("where am i", "my location", "gps", "near me", "current location", "locate me")
    private val liveWords = listOf(
        "traffic", "delay", "delayed", "delays", "late", "eta", "live", "crowd", "crowded", "rush",
        "weather", "rain", "raining", "flood", "flooded", "flooding", "closed", "closure", "strike",
        "cancelled", "canceled", "timing", "timings", "schedule", "timetable", "fare", "fares",
        "next train", "next bus", "which platform", "how long", "when will", "what time", "arrival time",
    )
    private val stopSpeakingExact = listOf("stop", "stop it")
    private val stopSpeaking = listOf("stop talking", "stop speaking", "stop reading", "quiet", "be quiet", "silence", "mute")
    private val end = listOf(
        "end", "end journey", "end trip", "finish", "finish journey", "finished", "arrived",
        "i have arrived", "ive arrived", "reached", "journey done", "trip done",
    )
    private val explain = listOf("explain", "why", "more detail", "more details", "details", "tell me more", "what does this mean")
    private val dataAge = listOf(
        "how old", "data age", "when was this saved", "when saved", "saved when", "last updated",
        "how fresh", "source", "up to date", "updated",
    )
    private val openMaps = listOf("maps", "map", "google maps", "navigate", "navigation")
    private val previous = listOf("previous", "back", "go back", "undo")
    private val next = listOf("next", "continue", "go on", "done", "what now", "whats next", "then what", "move on", "forward")
    private val repeat = listOf("repeat", "again", "say again", "say that again", "what was that", "current step", "this step", "read", "read aloud", "pardon")
    private val show = listOf("show", "show journey", "show steps", "all steps", "list", "overview", "saved journey", "my journey", "steps")

    fun parse(input: String): LocalCommand {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return LocalCommand.Unsupported(Reason.EMPTY)
        if (trimmed.length > MAX_INPUT_CHARS) return LocalCommand.Unsupported(Reason.TOO_LONG)
        val text = normalize(trimmed)
        if (text.isEmpty()) return LocalCommand.Unsupported(Reason.NOT_UNDERSTOOD)
        val words = text.split(' ')

        return when {
            hasAny(text, emergency) -> LocalCommand.Unsupported(Reason.EMERGENCY)
            text in help || hasAny(text, help.drop(1)) -> LocalCommand.Help
            hasAny(text, location) -> LocalCommand.Unsupported(Reason.LOCATION_NOT_USED)
            hasAny(text, liveWords) -> LocalCommand.LiveInfoUnavailable
            else -> stepNumber(words)?.let { LocalCommand.GoToStep(it) }
                ?: keywordCommand(text)
                ?: LocalCommand.Unsupported(Reason.NOT_UNDERSTOOD)
        }
    }

    private fun keywordCommand(text: String): LocalCommand? = when {
        text in stopSpeakingExact || hasAny(text, stopSpeaking) -> LocalCommand.StopSpeaking
        hasAny(text, end) -> LocalCommand.EndJourney
        hasAny(text, explain) -> LocalCommand.ExplainSavedStep
        hasAny(text, dataAge) -> LocalCommand.ShowDataAge
        hasAny(text, openMaps) -> LocalCommand.OpenGoogleMapsDestination
        hasAny(text, previous) -> LocalCommand.PreviousStep
        hasAny(text, next) -> LocalCommand.NextStep
        hasAny(text, repeat) -> LocalCommand.RepeatInstruction
        hasAny(text, show) -> LocalCommand.ShowSavedJourney
        else -> null
    }

    /** "step 3", "go to step three", "back to step 2", "the third step". */
    private fun stepNumber(words: List<String>): Int? {
        words.forEachIndexed { i, word ->
            if (word != "step") return@forEachIndexed
            words.getOrNull(i + 1)?.let(::cardinal)?.let { return it }
            words.getOrNull(i - 1)?.let { ordinalWords[it] }?.let { return it }
        }
        return null
    }

    private fun cardinal(word: String): Int? =
        word.toIntOrNull()?.takeIf { it in 0..999 } ?: numberWords[word] ?: ordinalWords[word]

    private fun hasAny(text: String, phrases: List<String>): Boolean {
        val padded = " $text "
        return phrases.any { padded.contains(" $it ") }
    }

    /** Lowercase, drop apostrophes, turn every non letter/digit run into one space. */
    internal fun normalize(input: String): String = input
        .lowercase(Locale.ROOT)
        .replace("'", "")
        .replace("’", "")
        .replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), " ")
        .trim()
}
