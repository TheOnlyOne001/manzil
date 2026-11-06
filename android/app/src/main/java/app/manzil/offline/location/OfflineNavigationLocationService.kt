package app.manzil.offline.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.manzil.R
import app.manzil.offline.OfflineActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * User-started GPS ownership during an active walk. START_NOT_STICKY: no boot receiver, no restored
 * navigation auto-start, no background location permission. A saved route is not an active journey.
 * Route tracking remains in the process's guidance controller; this service keeps GPS callbacks
 * alive when the Activity backgrounds or the display locks. Process death ends the session.
 */
class OfflineNavigationLocationService : Service() {
    private val session by lazy { OfflineLocationSession.get(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ownsGps = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            fail("Precise location permission is required to continue guidance in the background.")
            return START_NOT_STICKY
        }
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Offline walking guidance", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps GPS active only while you follow an offline walking route."
                setShowBadge(false)
            })
            val initialNotification = notification("Waiting for GPS. Your route is stored on this phone.")
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(NOTIFICATION_ID, initialNotification)
            if (!ownsGps) {
                ownsGps = true
                session.serviceStarted()
                _running.value = true
                scope.launch {
                    session.gps.state.map { it.status }.distinctUntilChanged().collect { status ->
                        if (status == GpsStatus.PERMISSION_REQUIRED) {
                            fail("Location permission was removed. Offline guidance stopped.")
                        } else {
                            val text = when (status) {
                                GpsStatus.FIX -> "GPS active. Offline guidance continues with the screen locked."
                                GpsStatus.PROVIDER_DISABLED -> "Location is off. Turn it on to continue guidance."
                                GpsStatus.STALE -> "GPS signal is stale. Waiting for a fresh position."
                                GpsStatus.ERROR -> "GPS unavailable. Open Manzil to check guidance."
                                else -> "Waiting for GPS. Your route is stored on this phone."
                            }
                            try { manager.notify(NOTIFICATION_ID, notification(text)) } catch (_: SecurityException) { /* Notification permission optional for FGS. */ }
                        }
                    }
                }
            }
        } catch (e: RuntimeException) {
            fail("Background guidance could not start. Keep Manzil open and try again (${e.javaClass.simpleName}).")
        }
        return START_NOT_STICKY
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 811, Intent(this, OfflineActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 812, Intent(this, OfflineNavigationLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_manzil)
            .setContentTitle("Manzil · offline walking")
            .setContentText(text)
            .setContentIntent(open)
            .setOnlyAlertOnce(true).setOngoing(true).setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Stop guidance", stop)
            .build()
    }

    private fun fail(message: String) { session.reportError(message); stopSelf() }

    override fun onDestroy() {
        scope.cancel()
        if (ownsGps) { ownsGps = false; session.serviceStopped() }
        _running.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "app.manzil.offline.location.START"
        private const val ACTION_STOP = "app.manzil.offline.location.STOP"
        private const val CHANNEL_ID = "offline_walking_guidance"
        private const val NOTIFICATION_ID = 812
        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        /** Invoke only on an explicit Start walk action while the Activity is resumed. */
        fun start(context: Context): Result<Unit> = runCatching {
            check(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                "Precise location permission is required."
            }
            ContextCompat.startForegroundService(context, Intent(context, OfflineNavigationLocationService::class.java).setAction(ACTION_START))
            Unit
        }.onFailure { OfflineLocationSession.get(context).reportError(it.message ?: "Could not start background guidance.") }

        fun stop(context: Context) { context.stopService(Intent(context, OfflineNavigationLocationService::class.java)) }
    }
}
