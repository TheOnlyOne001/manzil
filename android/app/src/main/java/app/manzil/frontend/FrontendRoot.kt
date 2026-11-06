package app.manzil.frontend

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.manzil.agent.LocalCommand
import app.manzil.model.SampleJourneys
import app.manzil.model.TimeText
import app.manzil.system.*
import app.manzil.ui.*
import java.time.ZoneId

@Composable
fun FrontendRoot(vm: ManzilViewModel, overlayControl: OverlayControl) {
    val stored by vm.stored.collectAsStateWithLifecycle()
    val speech by vm.speech.collectAsStateWithLifecycle()
    val network by vm.network.collectAsStateWithLifecycle()
    val reply by vm.reply.collectAsStateWithLifecycle()
    val permission by vm.overlayPermission.collectAsStateWithLifecycle()
    var asking by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(vm) {
        vm.effects.collect { effect -> when(effect) {
            is UiEffect.OpenMapsDestination -> vm.reportMapsResult(MapsLauncher.openDestination(context, effect.destination), effect.destination)
            UiEffect.OpenGoogleMapsApp -> vm.reportMapsAppResult(MapsLauncher.openGoogleMapsApp(context))
        } }
    }
    val saved = stored.find(SampleJourneys.CHENNAI_CENTRAL_ID)
    val journey = saved?.journey ?: SampleJourneys.chennaiCentral
    JourneyScreen(
        saved = saved != null, active = stored.hasJourneyInProgress,
        stepIndex = stored.progress?.stepIndex ?: 0,
        steps = journey.steps.map { JourneyDisplayStep(it.title, it.detail) },
        savedAt = saved?.let { "Saved " + TimeText.savedAt(it.savedAtEpochMs, System.currentTimeMillis(), ZoneId.systemDefault()) }.orEmpty(),
        connection = if(network == NetworkStatus.ONLINE) "Online" else "Offline",
        speechReady = speech is SpeechState.Ready,
        onSave = vm::saveSampleJourney,
        onStart = { vm.startJourney(journey.id) },
        onSelectStep = { if(stored.progress != null) vm.perform(LocalCommand.GoToStep(it + 1)) else vm.startJourney(journey.id) },
        onNext = { vm.perform(LocalCommand.NextStep) },
        onRead = vm::readCurrentStepAloud,
        onAsk = { vm.clearReply(); asking = true },
        onMaps = { if(stored.progress != null) vm.perform(LocalCommand.OpenGoogleMapsDestination) else vm.openGoogleMapsApp() },
        onOverlay = { if(permission) overlayControl.startBubble() else overlayControl.requestOverlayPermission() },
        onEnd = { overlayControl.stopBubble(); vm.perform(LocalCommand.EndJourney) },
    )
    if(asking) PandaCommandSheet(reply?.message, vm::submitText) { asking = false }
}
