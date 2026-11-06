package app.manzil.live

import android.app.Activity
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.StateFlow

/**
 * Public API for live Google Maps observation. Safe to call from UI code (main thread).
 *
 * - [state] / [snapshot]: the last Maps notification observation and how current it is.
 * - [isNotificationAccessEnabled] / [openNotificationAccessSettings]: user opt-in. Manzil never
 *   enables access itself; the user toggles it in system Settings.
 *
 * A snapshot reflects only what Google Maps put in its notification. It is not a GPS fix, does not
 * prove navigation is running, and may omit ETA, distance or turns entirely.
 */
class LiveNavigationRepository internal constructor(
    private val context: Context,
    private val store: LiveNavigationStore,
) {
    val state: StateFlow<LiveNavigationState> get() = store.state

    val listenerComponent: ComponentName get() = listenerComponent(context)

    fun snapshot(nowMs: Long = System.currentTimeMillis()): LiveNavigationSnapshot =
        LiveNavigationSnapshot.from(store.state.value, isNotificationAccessEnabled(), nowMs)

    fun isNotificationAccessEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= 27) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm != null) {
                return try {
                    nm.isNotificationListenerAccessGranted(listenerComponent)
                } catch (e: RuntimeException) {
                    false
                }
            }
        }
        return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }

    /** Intent to the system screen where the user can grant/revoke access. */
    fun notificationAccessSettingsIntent(): Intent {
        // Detail screen (API 30+) may be missing on some OEM builds; openNotificationAccessSettings
        // falls back to the generic list on ActivityNotFoundException.
        if (Build.VERSION.SDK_INT >= 30) {
            return Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listenerComponent.flattenToString())
        }
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }

    /** Opens the settings screen; from a non-Activity context adds NEW_TASK. Returns false if unavailable. */
    fun openNotificationAccessSettings(from: Context = context): Boolean {
        val primary = notificationAccessSettingsIntent()
        val fallback = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        for (intent in listOf(primary, fallback)) {
            if (from !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                from.startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
                // try the generic list next
            } catch (e: SecurityException) {
                // ditto
            }
        }
        return false
    }

    /** Ask the system to (re)bind the listener if access is granted but it is not connected. */
    fun requestRebind() {
        if (!isNotificationAccessEnabled()) return
        try {
            NotificationListenerService.requestRebind(listenerComponent)
        } catch (e: RuntimeException) {
            // not fatal; the system binds on its own schedule
        }
    }

    /** User-initiated: delete the saved observation from memory and disk. */
    fun clearSavedObservation() = store.clear()

    companion object {
        @Volatile
        private var instance: LiveNavigationRepository? = null

        fun get(context: Context): LiveNavigationRepository =
            instance ?: synchronized(this) {
                instance ?: LiveNavigationRepository(
                    context.applicationContext,
                    LiveNavigationStore.get(context),
                ).also { instance = it }
            }

        fun listenerComponent(context: Context): ComponentName =
            ComponentName(context.applicationContext, MapsNotificationListener::class.java)
    }
}
