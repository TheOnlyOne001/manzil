package app.manzil.live

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * USER-ENABLED notification listener (Settings › Notification access). It reacts only to
 * notifications whose package is Google Maps; every other notification is dropped on the package
 * check before its extras are touched, and nothing about it is logged, kept or persisted.
 *
 * It does not read the screen, use accessibility, location or network, or act on Maps in any way.
 */
class MapsNotificationListener : NotificationListenerService() {

    private val store get() = LiveNavigationStore.get(this)

    override fun onListenerConnected() {
        super.onListenerConnected()
        val active: Array<StatusBarNotification> = try {
            activeNotifications ?: emptyArray()
        } catch (e: RuntimeException) {
            // SecurityException if access was revoked mid-bind; treat as "nothing visible".
            emptyArray()
        }
        val candidates = active
            .filter { MapsNotificationFilter.isMapsPackage(it.packageName) }
            .mapNotNull { toObservation(it) }
        store.onListenerConnected(candidates)
    }

    override fun onListenerDisconnected() {
        store.onListenerDisconnected()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || !MapsNotificationFilter.isMapsPackage(sbn.packageName)) return
        toObservation(sbn)?.let { store.onPosted(it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null || !MapsNotificationFilter.isMapsPackage(sbn.packageName)) return
        store.onRemoved(sbn.key)
    }

    /** Caller must already have checked the package is Google Maps. */
    private fun toObservation(sbn: StatusBarNotification): MapsObservation? {
        val n: Notification = sbn.notification ?: return null
        val extras = n.extras
        return MapsNotificationFilter.extract(
            RawNotification(
                packageName = sbn.packageName,
                key = sbn.key,
                category = n.category,
                channelId = n.channelId,
                ongoing = sbn.isOngoing || (n.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0,
                groupSummary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
                title = extras?.getCharSequence(Notification.EXTRA_TITLE),
                text = extras?.getCharSequence(Notification.EXTRA_TEXT),
                bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT),
                postTimeMs = sbn.postTime,
            ),
        )
    }
}
