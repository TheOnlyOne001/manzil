package app.manzil

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import app.manzil.overlay.OverlayService
import app.manzil.frontend.LiveFrontendRoot
import app.manzil.ui.ManzilViewModel
import app.manzil.ui.OverlayControl
import app.manzil.ui.theme.ManzilTheme

class MainActivity : ComponentActivity(), OverlayControl {

    companion object {
        const val EXTRA_OPEN_JOURNEY = "app.manzil.extra.OPEN_JOURNEY"
    }

    private val vm: ManzilViewModel by viewModels()

    /** Set when the user asked for the bubble and was sent to grant the overlay permission. */
    private var bubbleRequestedBeforeGrant = false
    private var notificationAsked = false

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // The bubble works either way; the permission only makes its ongoing notification visible.
        startBubbleNow()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Light palette only in this slice, so always request dark system-bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(EXTRA_OPEN_JOURNEY, false) != true) {
            startActivity(Intent(this, app.manzil.offline.OfflineActivity::class.java))
            finish()
            return
        }
        bubbleRequestedBeforeGrant = savedInstanceState?.getBoolean("bubble_requested") ?: false
        handleIntent(intent)
        setContent {
            ManzilTheme {
                LiveFrontendRoot(overlayControl = this)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("bubble_requested", bubbleRequestedBeforeGrant)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        val granted = Settings.canDrawOverlays(this)
        vm.setOverlayPermission(granted)
        if (granted && bubbleRequestedBeforeGrant) {
            bubbleRequestedBeforeGrant = false
            startBubble()
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_JOURNEY, false) == true) {
            vm.openJourneyFromExternal()
            intent.removeExtra(EXTRA_OPEN_JOURNEY)
        }
    }

    // ---- OverlayControl -----------------------------------------------------------------------

    override fun requestOverlayPermission() {
        bubbleRequestedBeforeGrant = true
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        try {
            startActivity(intent)
        } catch (e: RuntimeException) {
            // Some OEM builds lack the per-app screen; fall back to the general list.
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            } catch (ignored: RuntimeException) {
                bubbleRequestedBeforeGrant = false
            }
        }
    }

    override fun startBubble() {
        // Only from a visible, resumed activity, and only with the user's overlay grant.
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            return
        }
        // Ask once per screen session; declining still starts the bubble.
        val needsNotificationPermission = Build.VERSION.SDK_INT >= 33 &&
            !notificationAsked &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) {
            notificationAsked = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startBubbleNow()
        }
    }

    private fun startBubbleNow() {
        if (!Settings.canDrawOverlays(this)) return
        OverlayService.startLive(this)
    }

    override fun stopBubble() {
        OverlayService.stop(this)
    }
}


