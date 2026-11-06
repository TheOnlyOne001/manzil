package app.manzil.agent

import app.manzil.model.Destination

/**
 * The complete allowlist of local tools. Nothing outside this sealed hierarchy can be executed:
 * there is no shell, package name, URL, coordinate-tap or free-form action.
 */
sealed interface LocalCommand {
    val toolName: String

    data object ShowSavedJourney : LocalCommand { override val toolName = "show_saved_journey" }
    data object NextStep : LocalCommand { override val toolName = "next_step" }
    data object PreviousStep : LocalCommand { override val toolName = "previous_step" }
    data object RepeatInstruction : LocalCommand { override val toolName = "repeat_instruction" }
    data object ExplainSavedStep : LocalCommand { override val toolName = "explain_saved_step" }
    data object ShowDataAge : LocalCommand { override val toolName = "show_data_age" }
    data object OpenGoogleMapsDestination : LocalCommand { override val toolName = "open_google_maps_destination" }
    data object EndJourney : LocalCommand { override val toolName = "end_journey" }

    /** 1-based step number as the user says it. Range is validated against the saved journey. */
    data class GoToStep(val stepNumber: Int) : LocalCommand { override val toolName = "select_step" }

    data object StopSpeaking : LocalCommand { override val toolName = "stop_speaking" }
    data object Help : LocalCommand { override val toolName = "list_commands" }

    /** Request for live facts (traffic, times, closures...) that Manzil does not have offline. */
    data object LiveInfoUnavailable : LocalCommand { override val toolName = "live_info_unavailable" }

    data class Unsupported(val reason: Reason) : LocalCommand {
        override val toolName = "unsupported"

        enum class Reason { EMPTY, TOO_LONG, NOT_UNDERSTOOD, LOCATION_NOT_USED, EMERGENCY }
    }
}

/** Side effect requested by a successful tool. The UI performs it; the dispatcher stays pure. */
sealed interface CommandEffect {
    data object None : CommandEffect
    data class Speak(val text: String) : CommandEffect
    data object StopSpeech : CommandEffect
    data object ShowJourney : CommandEffect
    data class OpenMapsDestination(val destination: Destination) : CommandEffect
}

/** Every tool returns success/failure plus the progress state version it left behind. */
data class CommandOutcome(
    val tool: String,
    val success: Boolean,
    val message: String,
    val stateVersion: Long,
    val effect: CommandEffect = CommandEffect.None,
)
