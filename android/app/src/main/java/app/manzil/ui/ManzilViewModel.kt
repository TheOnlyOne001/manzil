package app.manzil.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.manzil.R
import app.manzil.agent.CommandEffect
import app.manzil.agent.CommandOutcome
import app.manzil.agent.CommandParser
import app.manzil.agent.LocalCommand
import app.manzil.appGraph
import app.manzil.model.Destination
import app.manzil.model.SampleJourneys
import app.manzil.model.StoredState
import app.manzil.system.MapsLaunchResult
import app.manzil.system.NetworkStatus
import app.manzil.system.SpeechState
import app.manzil.system.currentNetworkStatus
import app.manzil.system.networkStatusFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Route { HOME, JOURNEY }

data class PandaReply(val id: Int, val message: String, val success: Boolean)

/** One-shot work that needs an Activity context. */
sealed interface UiEffect {
    data class OpenMapsDestination(val destination: Destination) : UiEffect
    data object OpenGoogleMapsApp : UiEffect
}

class ManzilViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = application.appGraph
    private val controller = graph.controller
    private val speechOutput = graph.speech

    val stored: StateFlow<StoredState> = controller.state
    val speech: StateFlow<SpeechState> = speechOutput.state
    val network: StateFlow<NetworkStatus> = networkStatusFlow(application)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), currentNetworkStatus(application))

    private val _route = MutableStateFlow<Route?>(null)
    val route: StateFlow<Route?> = _route.asStateFlow()

    private val _reply = MutableStateFlow<PandaReply?>(null)
    val reply: StateFlow<PandaReply?> = _reply.asStateFlow()

    private val _homeNotice = MutableStateFlow<String?>(null)
    val homeNotice: StateFlow<String?> = _homeNotice.asStateFlow()

    private val _speakSteps = MutableStateFlow(graph.settings.speakSteps)
    val speakSteps: StateFlow<Boolean> = _speakSteps.asStateFlow()

    private val _overlayPermission = MutableStateFlow(false)
    val overlayPermission: StateFlow<Boolean> = _overlayPermission.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects: Flow<UiEffect> = _effects.receiveAsFlow()

    private var replyCounter = 0
    private var openJourneyWhenLoaded = false

    init {
        speechOutput.start()
        viewModelScope.launch {
            val state = controller.load()
            _route.value = if (state.hasJourneyInProgress || (openJourneyWhenLoaded && state.activeJourney != null)) {
                Route.JOURNEY
            } else {
                Route.HOME
            }
        }
    }

    private fun str(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    // ---- navigation ---------------------------------------------------------------------------

    fun openJourneyFromExternal() {
        if (_route.value == null) {
            openJourneyWhenLoaded = true
        } else if (stored.value.activeJourney != null) {
            _route.value = Route.JOURNEY
        }
    }

    fun goHome() {
        _route.value = Route.HOME
    }

    // ---- home actions -------------------------------------------------------------------------

    fun saveSampleJourney() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val ok = controller.saveJourney(SampleJourneys.chennaiCentral)
            _homeNotice.value = if (ok) null else str(R.string.notice_save_failed)
            _busy.value = false
        }
    }

    fun startJourney(journeyId: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val outcome = controller.start(journeyId)
            _busy.value = false
            if (outcome.success) {
                _homeNotice.value = null
                postReply(outcome.message, true)
                _route.value = Route.JOURNEY
            } else {
                _homeNotice.value = outcome.message
            }
        }
    }

    fun removeJourney(journeyId: String) {
        viewModelScope.launch {
            val ok = controller.removeJourney(journeyId)
            _homeNotice.value = if (ok) null else str(R.string.notice_remove_failed)
            if (ok) _reply.value = null
        }
    }

    fun openGoogleMapsApp() {
        viewModelScope.launch { _effects.send(UiEffect.OpenGoogleMapsApp) }
    }

    fun reportMapsAppResult(opened: Boolean) {
        _homeNotice.value = if (opened) null else str(R.string.notice_maps_missing)
    }

    // ---- journey commands ---------------------------------------------------------------------

    /** Typed tool call from a button or chip. No parsing involved. */
    fun perform(command: LocalCommand) {
        viewModelScope.launch { handle(controller.execute(command)) }
    }

    /** Free text from the command sheet, through the deterministic parser only. */
    fun submitText(text: String) = perform(CommandParser.parse(text))

    private fun handle(outcome: CommandOutcome) {
        when (val effect = outcome.effect) {
            is CommandEffect.Speak -> if (_speakSteps.value) speechOutput.speak(effect.text)
            CommandEffect.StopSpeech -> speechOutput.stop()
            is CommandEffect.OpenMapsDestination -> {
                // Report only after the hand-off actually happened (see reportMapsResult).
                viewModelScope.launch { _effects.send(UiEffect.OpenMapsDestination(effect.destination)) }
                return
            }
            CommandEffect.ShowJourney -> if (stored.value.activeJourney != null) _route.value = Route.JOURNEY
            CommandEffect.None -> Unit
        }
        postReply(outcome.message, outcome.success)
    }

    fun reportMapsResult(result: MapsLaunchResult, destination: Destination) {
        when (result) {
            MapsLaunchResult.OPENED_GOOGLE_MAPS -> postReply(str(R.string.reply_maps_opened, destination.name), true)
            MapsLaunchResult.OPENED_OTHER_APP -> postReply(str(R.string.reply_maps_other_app, destination.name), true)
            MapsLaunchResult.NOT_AVAILABLE -> postReply(str(R.string.reply_maps_unavailable, destination.name), false)
        }
    }

    fun readCurrentStepAloud() {
        val state = stored.value
        val saved = state.activeJourney ?: return
        val progress = state.progress ?: return
        val index = progress.stepIndex.coerceIn(0, saved.journey.steps.lastIndex)
        val text = app.manzil.agent.CommandDispatcher.describeStep(saved.journey, index)
        if (!speechOutput.speak(text)) postReply(str(R.string.reply_no_voice), false)
    }

    fun setSpeakSteps(enabled: Boolean) {
        graph.settings.speakSteps = enabled
        _speakSteps.value = enabled
        if (!enabled) speechOutput.stop()
    }

    fun selectVoice(name: String) = speechOutput.selectVoice(name)

    fun setOverlayPermission(granted: Boolean) {
        _overlayPermission.value = granted
    }

    fun clearReply() {
        _reply.value = null
    }

    private fun postReply(message: String, success: Boolean) {
        replyCounter += 1
        _reply.value = PandaReply(replyCounter, message, success)
    }
}
