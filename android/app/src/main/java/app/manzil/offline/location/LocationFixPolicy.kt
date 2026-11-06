package app.manzil.offline.location

/** Where a fix came from. Raw GNSS keeps working with no network and no Play Services. */
enum class FixSource { GPS, FUSED }

/**
 * Pure (Android-free) rules for merging two independent live streams — raw GPS_PROVIDER and the
 * fused provider — plus cached last-known values. Nothing here invents or edits a position: a
 * candidate is either accepted verbatim or rejected.
 */
internal object LocationFixPolicy {
    /** After this long without a newer usable fix, any newer real fix may take over. */
    const val DISPLACE_AFTER_MILLIS = 5_000L
    /** Absolute slack (m) so ordinary jitter between GPS and fused estimates doesn't flap. */
    const val ACCURACY_SLACK_METERS = 10f

    /**
     * Whether a live candidate should replace [current].
     * - Cached candidates are never handled here (see [shouldReplaceCached]).
     * - Older or same-instant fixes are rejected (the same GNSS fix often arrives via both paths).
     * - Within [DISPLACE_AFTER_MILLIS] a markedly less accurate fix (e.g. a Wi-Fi/cell estimate from
     *   the fused provider) cannot displace a usable GPS-grade fix.
     */
    fun shouldReplaceLive(current: GpsFix?, candidate: GpsFix): Boolean {
        if (candidate.cached) return false
        if (current == null || current.cached) return true
        val newer = candidate.elapsedRealtimeMillis - current.elapsedRealtimeMillis
        if (newer <= 0) return false
        if (newer >= DISPLACE_AFTER_MILLIS) return true
        val currentAccuracy = current.accuracyMeters
        val candidateAccuracy = candidate.accuracyMeters
        if (currentAccuracy == null) return true
        if (candidateAccuracy == null) return false
        val tolerance = maxOf(ACCURACY_SLACK_METERS, currentAccuracy * 0.5f)
        return candidateAccuracy <= currentAccuracy + tolerance
    }

    /** A cached last-known value may only fill an empty slot or replace an older cached value. */
    fun shouldReplaceCached(current: GpsFix?, candidate: GpsFix): Boolean {
        if (!candidate.cached) return false
        if (current == null) return true
        return current.cached && candidate.elapsedRealtimeMillis > current.elapsedRealtimeMillis
    }

    /** Status after accepting a fix. A cached fix never reports FIX, however recent it looks. */
    fun statusFor(fix: GpsFix, nowElapsedRealtimeMillis: Long, staleAfterMillis: Long, waitingStatus: GpsStatus): GpsStatus = when {
        fix.cached -> waitingStatus
        fix.ageMillis(nowElapsedRealtimeMillis) > staleAfterMillis -> GpsStatus.STALE
        else -> GpsStatus.FIX
    }

    /**
     * Walking guidance needs precise location. With only approximate (coarse) permission we do not
     * start, rather than silently navigating on a ~2 km-fuzzed position. Returns an error or null.
     */
    fun permissionError(fineGranted: Boolean, coarseGranted: Boolean): String? = when {
        fineGranted -> null
        coarseGranted -> "Only approximate location is allowed. Turn on Precise location for walking guidance."
        else -> "Precise location permission is needed for walking guidance."
    }
}
