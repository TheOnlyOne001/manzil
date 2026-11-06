package app.manzil.offline.location

import org.junit.Assert.*
import org.junit.Test

class RouteProgressTest {
    private val route = listOf(RoutePoint(13.0, 80.0), RoutePoint(13.01, 80.0))
    private fun fix(lat: Double, lon: Double = 80.0, at: Long = 1000, accuracy: Double = 5.0, cached: Boolean = false) =
        NavigationFix(RoutePoint(lat, lon), accuracy, at, cached)

    @Test fun measuresRealSegmentDistanceAndRemaining() {
        val tracker = RouteProgressTracker(route)
        val progress = tracker.update(fix(13.005), 1000)
        assertEquals(TrackingQuality.TRACKING, progress.quality)
        assertEquals(556.0, progress.traveledMeters, 2.0)
        assertEquals(556.0, progress.remainingMeters, 2.0)
        assertFalse(progress.shouldReroute)
    }

    @Test fun cachedStaleAndInaccurateFixesNeverTriggerReroute() {
        val tracker = RouteProgressTracker(route)
        assertEquals(TrackingQuality.STALE_FIX, tracker.update(fix(13.005, 80.02, cached = true), 1000).quality)
        assertEquals(TrackingQuality.STALE_FIX, tracker.update(fix(13.005, 80.02), 20_000).quality)
        assertEquals(TrackingQuality.POOR_ACCURACY, tracker.update(fix(13.005, 80.02, accuracy = 200.0), 1000).quality)
        assertFalse(tracker.update(fix(13.005, 80.02, at = 2000), 2000).shouldReroute)
    }

    @Test fun threeDistinctFreshOffrouteFixesRequiredAndDuplicatesDoNotCount() {
        val tracker = RouteProgressTracker(route)
        assertFalse(tracker.update(fix(13.005, 80.002), 1000).shouldReroute)
        assertFalse(tracker.update(fix(13.005, 80.002), 1000).shouldReroute)
        assertFalse(tracker.update(fix(13.005, 80.002, at = 2000), 2000).shouldReroute)
        assertTrue(tracker.update(fix(13.005, 80.002, at = 3000), 3000).shouldReroute)
    }

    @Test fun timeGapBreaksOffrouteHysteresis() {
        val tracker = RouteProgressTracker(route)
        tracker.update(fix(13.005, 80.002), 1000)
        tracker.update(fix(13.005, 80.002, at = 2000), 2000)
        val afterGap = tracker.update(fix(13.005, 80.002, at = 30_000), 30_000)
        assertFalse(afterGap.shouldReroute)
        assertEquals(1, afterGap.consecutiveOffRouteFixes)
    }

    @Test fun nearbyParallelRouteSectionDoesNotJumpProgress() {
        val loop = listOf(RoutePoint(13.0,80.0), RoutePoint(13.002,80.0), RoutePoint(13.002,80.0001), RoutePoint(13.0,80.0001))
        val tracker = RouteProgressTracker(loop)
        tracker.update(fix(13.0002),1000)
        val shifted = tracker.update(fix(13.00025,80.00008,at=2000),2000)
        assertTrue(shifted.traveledMeters < 50.0)
        assertEquals(0, shifted.segmentIndex)
    }

    @Test fun arrivalNeedsTwoAccurateDistinctEndpointFixes() {
        val tracker = RouteProgressTracker(route)
        assertEquals(TrackingQuality.TRACKING, tracker.update(fix(13.00999),1000).quality)
        assertEquals(TrackingQuality.TRACKING, tracker.update(fix(13.00999),1000).quality)
        assertEquals(TrackingQuality.ARRIVED, tracker.update(fix(13.01,at=2000),2000).quality)
    }

    @Test fun suspiciousTeleportDoesNotAdvanceToEnd() {
        val tracker = RouteProgressTracker(route)
        tracker.update(fix(13.0),1000)
        val jump = tracker.update(fix(13.01,at=2000),2000)
        assertEquals(TrackingQuality.UNCERTAIN_MATCH,jump.quality)
        assertEquals(0.0,jump.traveledMeters,0.1)
        assertFalse(jump.shouldReroute)
    }
}
