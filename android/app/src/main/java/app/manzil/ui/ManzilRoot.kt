package app.manzil.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.manzil.overlay.OverlayService
import app.manzil.system.MapsLauncher
import app.manzil.ui.theme.MzColors

/** Implemented by MainActivity: overlay start/stop must come from the visible activity. */
interface OverlayControl {
    fun requestOverlayPermission()
    fun startBubble()
    fun stopBubble()
}

@Composable
fun ManzilRoot(vm: ManzilViewModel, overlayControl: OverlayControl) {
    val route by vm.route.collectAsStateWithLifecycle()
    val stored by vm.stored.collectAsStateWithLifecycle()
    val network by vm.network.collectAsStateWithLifecycle()
    val speech by vm.speech.collectAsStateWithLifecycle()
    val reply by vm.reply.collectAsStateWithLifecycle()
    val notice by vm.homeNotice.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val speakSteps by vm.speakSteps.collectAsStateWithLifecycle()
    val overlayPermission by vm.overlayPermission.collectAsStateWithLifecycle()
    val overlayRunning by OverlayService.running.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(vm) {
        vm.effects.collect { effect ->
            when (effect) {
                is UiEffect.OpenMapsDestination ->
                    vm.reportMapsResult(MapsLauncher.openDestination(context, effect.destination), effect.destination)
                UiEffect.OpenGoogleMapsApp -> vm.reportMapsAppResult(MapsLauncher.openGoogleMapsApp(context))
            }
        }
    }

    Crossfade(targetState = route, animationSpec = tween(durationMillis = 240), label = "route") { current ->
        when (current) {
            null -> Box(Modifier.fillMaxSize().background(MzColors.Paper))
            Route.HOME -> HomeScreen(
                stored = stored,
                network = network,
                speech = speech,
                notice = notice,
                busy = busy,
                onSaveSample = vm::saveSampleJourney,
                onStart = vm::startJourney,
                onRemove = vm::removeJourney,
                onOpenGoogleMaps = vm::openGoogleMapsApp,
            )
            Route.JOURNEY -> {
                val saved = stored.activeJourney
                val progress = stored.progress
                if (saved == null || progress == null) {
                    LaunchedEffect(Unit) { vm.goHome() }
                    Box(Modifier.fillMaxSize().background(MzColors.Paper))
                } else {
                    JourneyScreen(
                        saved = saved,
                        progress = progress,
                        network = network,
                        speech = speech,
                        speakSteps = speakSteps,
                        reply = reply,
                        overlayPermission = overlayPermission,
                        overlayRunning = overlayRunning,
                        actions = JourneyActions(
                            onBack = vm::goHome,
                            onCommand = vm::perform,
                            onSubmitText = vm::submitText,
                            onReadAloud = vm::readCurrentStepAloud,
                            onSpeakStepsChange = vm::setSpeakSteps,
                            onSelectVoice = vm::selectVoice,
                            onBubbleChange = { on -> if (on) overlayControl.startBubble() else overlayControl.stopBubble() },
                            onRequestOverlayPermission = overlayControl::requestOverlayPermission,
                            onStartAgain = { vm.startJourney(saved.journey.id) },
                            onDismissReply = vm::clearReply,
                        ),
                    )
                }
            }
        }
    }
}
