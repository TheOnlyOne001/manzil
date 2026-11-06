package app.manzil.live

import org.json.JSONObject

enum class ObservationStatus {
    /** Last event for this key was a post/update (it may still be on screen). */
    ACTIVE,

    /** Maps removed the notification, or it was absent when the listener (re)connected. */
    REMOVED,
}

/** The one durable "saved observation". Only Google Maps data ever reaches this type. */
data class SavedObservation(
    val observation: MapsObservation,
    val status: ObservationStatus,
    /** Device clock when Manzil last received this content. */
    val receivedAtMs: Long,
    /** Device clock when [status] last changed. */
    val statusChangedAtMs: Long,
)

data class LiveNavigationState(
    val saved: SavedObservation? = null,
    /** The system has bound Manzil's notification listener in this process right now. */
    val listenerConnected: Boolean = false,
    /** [saved] was received (not merely loaded from disk) during this process lifetime. */
    val observedThisSession: Boolean = false,
)

enum class LiveFreshness {
    /** Nothing observed yet. */
    NONE,

    /** Maps notification received this session, still posted, recently updated, listener bound. */
    CURRENT,

    /** Durable copy that cannot be confirmed current (old, loaded from disk, or listener unbound). */
    STALE,

    /** Maps removed the notification. */
    REMOVED,
}

enum class LiveFeedback { NONE, ACCESS_OFF, NOT_CONNECTED, WAITING, STALE, REMOVED }

/**
 * Read-only, UI-ready view. Note: even [LiveFreshness.CURRENT] only means "Google Maps is showing
 * this notification text"; it is NOT proof of a GPS fix or that navigation is progressing.
 */
data class LiveNavigationSnapshot(
    val accessEnabled: Boolean,
    val listenerConnected: Boolean,
    val freshness: LiveFreshness,
    val observation: MapsObservation?,
    val receivedAtMs: Long?,
    val statusChangedAtMs: Long?,
) {
    /** Text exactly as Maps provided it (normalized), for display. */
    val displayText: String? get() = observation?.displayText(" · ")

    /** Text for Repeat / speech: only the observed text. */
    val spokenText: String? get() = observation?.displayText(". ")

    val feedback: LiveFeedback
        get() = when {
            !accessEnabled -> LiveFeedback.ACCESS_OFF
            freshness == LiveFreshness.REMOVED -> LiveFeedback.REMOVED
            !listenerConnected -> LiveFeedback.NOT_CONNECTED
            freshness == LiveFreshness.STALE -> LiveFeedback.STALE
            freshness == LiveFreshness.NONE -> LiveFeedback.WAITING
            else -> LiveFeedback.NONE
        }

    companion object {
        /** A Maps navigation notification normally refreshes far more often than this. */
        const val STALE_AFTER_MS = 5 * 60 * 1000L

        fun from(
            state: LiveNavigationState,
            accessEnabled: Boolean,
            nowMs: Long,
            staleAfterMs: Long = STALE_AFTER_MS,
        ): LiveNavigationSnapshot {
            val saved = state.saved
            val freshness = when {
                saved == null -> LiveFreshness.NONE
                saved.status == ObservationStatus.REMOVED -> LiveFreshness.REMOVED
                accessEnabled && state.listenerConnected && state.observedThisSession &&
                    nowMs - saved.receivedAtMs in 0..staleAfterMs -> LiveFreshness.CURRENT
                else -> LiveFreshness.STALE
            }
            return LiveNavigationSnapshot(
                accessEnabled = accessEnabled,
                listenerConnected = state.listenerConnected,
                freshness = freshness,
                observation = saved?.observation,
                receivedAtMs = saved?.receivedAtMs,
                statusChangedAtMs = saved?.statusChangedAtMs,
            )
        }
    }
}

/** Versioned JSON for the single saved observation. Decoding re-normalizes and fails closed (null). */
object SavedObservationCodec {
    private const val VERSION = 1

    fun encode(saved: SavedObservation): String {
        val o = saved.observation
        return JSONObject()
            .put("v", VERSION)
            .put("key", o.key)
            .putOpt("title", o.title)
            .putOpt("text", o.text)
            .putOpt("bigText", o.bigText)
            .putOpt("category", o.category)
            .putOpt("channelId", o.channelId)
            .put("ongoing", o.ongoing)
            .put("sourcePostedAtMs", o.sourcePostedAtMs)
            .put("status", saved.status.name)
            .put("receivedAtMs", saved.receivedAtMs)
            .put("statusChangedAtMs", saved.statusChangedAtMs)
            .toString()
    }

    fun decode(json: String?): SavedObservation? {
        if (json.isNullOrBlank()) return null
        return try {
            val j = JSONObject(json)
            if (j.optInt("v", -1) != VERSION) return null
            val key = j.optString("key").takeIf { it.isNotBlank() } ?: return null
            fun str(name: String, max: Int = MapsNotificationFilter.MAX_FIELD_LENGTH) =
                if (j.isNull(name)) null else MapsNotificationFilter.normalize(j.optString(name), max)
            val observation = MapsObservation(
                key = key,
                title = str("title"),
                text = str("text"),
                bigText = str("bigText"),
                category = str("category", 64),
                channelId = str("channelId", 128),
                ongoing = j.optBoolean("ongoing", false),
                sourcePostedAtMs = j.optLong("sourcePostedAtMs", 0L),
            )
            if (observation.displayText() == null) return null
            SavedObservation(
                observation = observation,
                status = ObservationStatus.valueOf(j.getString("status")),
                receivedAtMs = j.getLong("receivedAtMs"),
                statusChangedAtMs = j.getLong("statusChangedAtMs"),
            )
        } catch (e: Exception) {
            null
        }
    }
}
