package app.manzil.offline.location

import org.junit.Assert.*
import org.junit.Test

class LocationFixPolicyTest {
    private fun fix(elapsed: Long, accuracy: Float?, source: FixSource = FixSource.GPS, cached: Boolean = false, lat: Double = 13.0827) =
        GpsFix(RoutePoint(lat, 80.2707), accuracy, null, null, null, epochMillis = 1_700_000_000_000L + elapsed, elapsedRealtimeMillis = elapsed, cached = cached, source = source)

    @Test fun firstLiveFixFromFusedIsAcceptedImmediately() {
        assertTrue(LocationFixPolicy.shouldReplaceLive(null, fix(1_000, 45f, FixSource.FUSED)))
    }

    @Test fun liveFixAlwaysReplacesCachedEvenIfLessAccurate() {
        val cached = fix(10_000, 4f, cached = true)
        assertTrue(LocationFixPolicy.shouldReplaceLive(cached, fix(9_000, 60f, FixSource.FUSED)))
    }

    @Test fun cachedNeverDisplacesLiveAndNeverReportsFix() {
        val live = fix(10_000, 8f)
        val newerCached = fix(20_000, 3f, cached = true)
        assertFalse(LocationFixPolicy.shouldReplaceCached(live, newerCached))
        assertFalse(LocationFixPolicy.shouldReplaceLive(live, newerCached))
        assertEquals(GpsStatus.WAITING_FOR_FIX,
            LocationFixPolicy.statusFor(newerCached, nowElapsedRealtimeMillis = 20_000, staleAfterMillis = 15_000, waitingStatus = GpsStatus.WAITING_FOR_FIX))
    }

    @Test fun cachedOnlyFillsEmptyOrOlderCached() {
        assertTrue(LocationFixPolicy.shouldReplaceCached(null, fix(5_000, 30f, cached = true)))
        assertTrue(LocationFixPolicy.shouldReplaceCached(fix(5_000, 30f, cached = true), fix(6_000, 90f, FixSource.FUSED, cached = true)))
        assertFalse(LocationFixPolicy.shouldReplaceCached(fix(6_000, 30f, cached = true), fix(5_000, 3f, cached = true)))
        assertFalse(LocationFixPolicy.shouldReplaceCached(null, fix(5_000, 3f, cached = false)))
    }

    @Test fun coarseNetworkFixCannotDisplaceRecentUsableGps() {
        val gps = fix(10_000, 6f)
        assertFalse(LocationFixPolicy.shouldReplaceLive(gps, fix(11_000, 40f, FixSource.FUSED)))
        assertFalse(LocationFixPolicy.shouldReplaceLive(gps, fix(14_999, 40f, FixSource.FUSED)))
        // Unknown accuracy is treated as worse than known accuracy.
        assertFalse(LocationFixPolicy.shouldReplaceLive(gps, fix(11_000, null, FixSource.FUSED)))
    }

    @Test fun comparableOrBetterNewerFixReplacesRegardlessOfSource() {
        val gps = fix(10_000, 6f)
        assertTrue(LocationFixPolicy.shouldReplaceLive(gps, fix(11_000, 12f, FixSource.FUSED))) // within 10 m slack
        assertTrue(LocationFixPolicy.shouldReplaceLive(gps, fix(11_000, 3f, FixSource.FUSED)))
        assertTrue(LocationFixPolicy.shouldReplaceLive(fix(10_000, 40f, FixSource.FUSED), fix(10_500, 5f)))
    }

    @Test fun gpsGoingQuietLetsNewerRealFixTakeOver() {
        val gps = fix(10_000, 6f)
        assertTrue(LocationFixPolicy.shouldReplaceLive(gps, fix(10_000 + LocationFixPolicy.DISPLACE_AFTER_MILLIS, 80f, FixSource.FUSED)))
    }

    @Test fun olderOrDuplicateInstantIsRejected() {
        val gps = fix(10_000, 6f)
        // Same GNSS fix echoed by the fused provider: no flapping, no re-emission.
        assertFalse(LocationFixPolicy.shouldReplaceLive(gps, fix(10_000, 6f, FixSource.FUSED)))
        assertFalse(LocationFixPolicy.shouldReplaceLive(gps, fix(9_000, 2f, FixSource.FUSED)))
    }

    @Test fun acceptedFixIsPassedThroughUnmodified() {
        val candidate = fix(11_000, 4.5f, FixSource.FUSED, lat = 13.0501)
        assertTrue(LocationFixPolicy.shouldReplaceLive(fix(10_000, 6f), candidate))
        val nav = candidate.forNavigation()
        assertEquals(11_000L, nav.elapsedRealtimeMillis)
        assertEquals(4.5, nav.accuracyMeters, 0.0)
        assertFalse(nav.cached)
        assertEquals(13.0501, candidate.point.latitude, 0.0)
    }

    @Test fun liveStatusTracksAgeFromElapsedRealtime() {
        val live = fix(10_000, 6f)
        assertEquals(GpsStatus.FIX, LocationFixPolicy.statusFor(live, 20_000, 15_000, GpsStatus.WAITING_FOR_FIX))
        assertEquals(GpsStatus.STALE, LocationFixPolicy.statusFor(live, 25_001, 15_000, GpsStatus.WAITING_FOR_FIX))
    }

    @Test fun approximateOnlyPermissionDoesNotStartGuidance() {
        assertNull(LocationFixPolicy.permissionError(fineGranted = true, coarseGranted = true))
        val coarse = LocationFixPolicy.permissionError(fineGranted = false, coarseGranted = true)
        assertNotNull(coarse)
        assertTrue(coarse!!.contains("Precise"))
        assertNotEquals(coarse, LocationFixPolicy.permissionError(fineGranted = false, coarseGranted = false))
    }
}
