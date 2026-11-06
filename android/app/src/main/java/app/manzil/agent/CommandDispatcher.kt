package app.manzil.agent

import app.manzil.agent.LocalCommand.Unsupported.Reason
import app.manzil.model.Journey
import app.manzil.model.JourneyProgress
import app.manzil.model.ProgressStatus
import app.manzil.model.SavedJourney
import app.manzil.model.StoredState
import app.manzil.model.TimeText
import java.time.ZoneId

data class DispatchContext(
    val state: StoredState,
    val nowEpochMs: Long,
    val zone: ZoneId,
)

/** [newProgress] is non-null only when the command changes durable progress. */
data class DispatchResult(
    val outcome: CommandOutcome,
    val newProgress: JourneyProgress?,
)

/**
 * Pure tool executor: (command, saved state) -> (outcome, new progress). It only ever reads the
 * saved journey; it never invents live information (ETA, platforms, closures, location).
 */
object CommandDispatcher {
    const val CAPABILITIES =
        "I can use your saved journey: next, previous, repeat, explain, \"step 3\", " +
            "\"how old is this\", open in Maps, and finish journey."

    fun dispatch(command: LocalCommand, context: DispatchContext): DispatchResult {
        val state = context.state
        val progress = state.progress
        val version = progress?.stateVersion ?: 0L

        fun result(
            success: Boolean,
            message: String,
            effect: CommandEffect = CommandEffect.None,
            newProgress: JourneyProgress? = null,
        ) = DispatchResult(
            CommandOutcome(command.toolName, success, message, newProgress?.stateVersion ?: version, effect),
            newProgress,
        )

        // Commands that do not need a journey in progress.
        when (command) {
            is LocalCommand.Unsupported -> return result(false, unsupportedMessage(command.reason))
            LocalCommand.Help -> return result(true, CAPABILITIES)
            LocalCommand.StopSpeaking -> return result(true, "Stopped reading aloud.", CommandEffect.StopSpeech)
            else -> Unit
        }

        val saved = state.activeJourney
        if (saved == null || progress == null) {
            return if (command == LocalCommand.LiveInfoUnavailable) {
                result(false, "I don't have live traffic, train times, platforms, closures, fares or weather. Check station boards or ask staff.")
            } else {
                result(false, "No saved journey is in progress. Start one from the home screen first.")
            }
        }

        val journey = saved.journey
        val steps = journey.steps
        val index = progress.stepIndex.coerceIn(0, steps.lastIndex)
        val finished = progress.status == ProgressStatus.FINISHED

        fun moveTo(target: Int): DispatchResult {
            val updated = progress.copy(
                stepIndex = target,
                status = ProgressStatus.ACTIVE,
                updatedAtEpochMs = context.nowEpochMs,
                stateVersion = progress.stateVersion + 1,
            )
            val text = describeStep(journey, target)
            return result(true, text, CommandEffect.Speak(text), updated)
        }

        return when (command) {
            LocalCommand.ShowSavedJourney ->
                result(true, "Showing all ${steps.size} saved steps to ${journey.destination.name}.", CommandEffect.ShowJourney)

            LocalCommand.NextStep -> when {
                finished -> result(false, FINISHED_MESSAGE)
                index >= steps.lastIndex ->
                    result(false, "You're on the last saved step. Say \"finish journey\" when you've arrived.")
                else -> moveTo(index + 1)
            }

            LocalCommand.PreviousStep -> when {
                finished -> result(false, FINISHED_MESSAGE)
                index == 0 -> result(false, "You're already on the first saved step.")
                else -> moveTo(index - 1)
            }

            is LocalCommand.GoToStep ->
                if (command.stepNumber !in 1..steps.size) {
                    result(false, "This journey has ${steps.size} saved steps. Choose a step from 1 to ${steps.size}.")
                } else {
                    moveTo(command.stepNumber - 1)
                }

            LocalCommand.RepeatInstruction -> {
                val text = describeStep(journey, index)
                result(true, text, CommandEffect.Speak(text))
            }

            LocalCommand.ExplainSavedStep -> {
                val explanation = steps[index].explanation
                result(true, "$explanation ${savedNote(saved, context)}", CommandEffect.Speak(explanation))
            }

            LocalCommand.ShowDataAge -> result(true, dataAge(saved, context))

            LocalCommand.OpenGoogleMapsDestination -> result(
                true,
                "Opening ${journey.destination.name} in Google Maps. Google Maps picks its own route; Manzil can't see it.",
                CommandEffect.OpenMapsDestination(journey.destination),
            )

            LocalCommand.EndJourney ->
                if (finished) {
                    result(true, "This journey is already finished. Your saved steps stay on this phone.")
                } else {
                    val updated = progress.copy(
                        status = ProgressStatus.FINISHED,
                        updatedAtEpochMs = context.nowEpochMs,
                        stateVersion = progress.stateVersion + 1,
                    )
                    result(true, "Journey finished. Your saved steps stay on this phone.", CommandEffect.StopSpeech, updated)
                }

            LocalCommand.LiveInfoUnavailable -> result(
                false,
                "I only have the steps saved ${TimeText.savedAt(saved.savedAtEpochMs, context.nowEpochMs, context.zone)}. " +
                    "I don't have live traffic, train times, platforms, closures, fares or weather. " +
                    "Check station boards or ask staff.",
            )

            // Handled before the journey check; listed so the when stays exhaustive.
            is LocalCommand.Unsupported -> result(false, unsupportedMessage(command.reason))
            LocalCommand.Help -> result(true, CAPABILITIES)
            LocalCommand.StopSpeaking -> result(true, "Stopped reading aloud.", CommandEffect.StopSpeech)
        }
    }

    fun describeStep(journey: Journey, index: Int): String {
        val step = journey.steps[index]
        val base = "Step ${index + 1} of ${journey.steps.size}: ${step.title}. ${step.detail}"
        return if (step.caution != null) "$base Note: ${step.caution}" else base
    }

    fun dataAge(saved: SavedJourney, context: DispatchContext): String = buildString {
        append("Saved to this phone ")
        append(TimeText.relative(context.nowEpochMs - saved.savedAtEpochMs))
        append(", ")
        append(TimeText.savedAt(saved.savedAtEpochMs, context.nowEpochMs, context.zone))
        append(". ")
        val prepared = TimeText.date(saved.journey.contentPreparedAtEpochMs, context.zone)
        if (saved.journey.isSample) {
            append("The content is sample data written $prepared, not verified transit directions. ")
        } else {
            append("The content was prepared $prepared. ")
        }
        append("No live updates have been received.")
    }

    private fun savedNote(saved: SavedJourney, context: DispatchContext): String =
        "This is saved text from ${TimeText.savedAt(saved.savedAtEpochMs, context.nowEpochMs, context.zone)}; " +
            "it can't see current conditions."

    fun unsupportedMessage(reason: Reason): String = when (reason) {
        Reason.EMPTY -> "Type a command, for example \"next\" or \"explain\"."
        Reason.TOO_LONG -> "That's longer than I can handle. Try a short command like \"next\" or \"step 3\"."
        Reason.LOCATION_NOT_USED ->
            "Manzil doesn't use your location in this version. Tell me your step instead, for example \"step 3\"."
        Reason.EMERGENCY ->
            "Manzil can't call anyone. In an emergency in India, dial 112 from your phone."
        Reason.NOT_UNDERSTOOD -> "I can't do that offline. $CAPABILITIES"
    }

    private const val FINISHED_MESSAGE =
        "This journey is finished. Choose a step to resume, or start it again from home."
}
