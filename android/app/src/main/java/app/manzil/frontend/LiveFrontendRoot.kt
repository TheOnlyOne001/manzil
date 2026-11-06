package app.manzil.frontend

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.manzil.live.*
import app.manzil.system.MapsLauncher
import app.manzil.ui.OverlayControl
import kotlinx.coroutines.delay

@Composable
fun LiveFrontendRoot(overlayControl: OverlayControl) {
    val context = LocalContext.current
    val repository = remember { LiveNavigationRepository.get(context) }
    val state by repository.state.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while(true) { delay(15000); now = System.currentTimeMillis() } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if(event == Lifecycle.Event.ON_RESUME) { now = System.currentTimeMillis(); repository.requestRebind() } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val snapshot = LiveNavigationSnapshot.from(state, repository.isNotificationAccessEnabled(), now)
    val age = snapshot.receivedAtMs?.let { ((now-it).coerceAtLeast(0)/1000) }
    val status = when {
        !snapshot.accessEnabled -> "Connect Google Maps directions once. Your existing route stays unchanged."
        snapshot.freshness == LiveFreshness.CURRENT -> "Google Maps notification received ${age ?: 0}s ago. This text does not confirm your current position."
        snapshot.freshness == LiveFreshness.REMOVED -> "Saved observation. Maps removed this notification; it is not a current instruction."
        snapshot.observation != null -> "Saved observation from ${age ?: 0}s ago. Current directions are unavailable."
        else -> "Waiting for Google Maps to share a navigation notification. No direction has been received yet."
    }
    LiveCompanionScreen(snapshot.displayText, status, snapshot.accessEnabled,
        onAccess = { repository.openNotificationAccessSettings(context) },
        onStart = overlayControl::startBubble,
        onMaps = { MapsLauncher.openGoogleMapsApp(context) },
        onStop = overlayControl::stopBubble)
}
