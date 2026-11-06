package app.manzil.offline.location

import org.junit.Assert.*
import org.junit.Test

class GpsOwnershipTest {
    @Test fun activityBackgroundDoesNotStopNavigationGps() {
        val ownership = GpsOwnership()
        ownership.acquirePreview()
        ownership.setService(true)
        ownership.releasePreview()
        assertTrue(ownership.shouldRun)
        ownership.setService(false)
        assertFalse(ownership.shouldRun)
    }
    @Test fun stoppingGuidanceKeepsVisiblePreviewButNeverAutostartsNewSession() {
        val ownership = GpsOwnership()
        ownership.acquirePreview()
        ownership.setService(true)
        ownership.setService(false)
        assertTrue(ownership.shouldRun)
        ownership.releasePreview()
        ownership.releasePreview()
        assertFalse(ownership.shouldRun)
        assertFalse(GpsOwnership().shouldRun)
    }
    @Test fun overlappingConfigurationLifecycleKeepsOneSourceUntilBothRelease() {
        val ownership = GpsOwnership()
        ownership.acquirePreview()
        ownership.acquirePreview()
        ownership.releasePreview()
        assertTrue(ownership.shouldRun)
        ownership.releasePreview()
        assertFalse(ownership.shouldRun)
    }
}
