package app.manzil.live
import org.junit.Assert.*
import org.junit.Test

class LiveObservationTest {
    private fun raw() = RawNotification("com.google.android.apps.maps", "key", "navigation", "nav", true, false, "Towards College Road", "Turn right", null, 1000L)
    @Test fun unrelatedPackagesAndSummaryAreRejected() {
        assertNull(MapsNotificationFilter.extract(raw().copy(packageName="com.example.messages")))
        assertNull(MapsNotificationFilter.extract(raw().copy(groupSummary=true)))
        assertNull(MapsNotificationFilter.extract(raw().copy(category="progress", ongoing=true)))
        assertNull(MapsNotificationFilter.extract(raw().copy(category=null, ongoing=false)))
    }
    @Test fun contentComesOnlyFromNotification() {
        assertEquals("Towards College Road · Turn right", MapsNotificationFilter.extract(raw())!!.displayText())
        assertNull(MapsNotificationFilter.extract(raw().copy(title=null,text="  ")))
    }
    @Test fun coldStartAndRevocationNeverProduceCurrentObservation() {
        val saved=SavedObservation(MapsNotificationFilter.extract(raw())!!, ObservationStatus.ACTIVE,1000,1000)
        val cold=LiveNavigationState(saved,true,false)
        assertEquals(LiveFreshness.STALE,LiveNavigationSnapshot.from(cold,true,2000).freshness)
        assertEquals(LiveFreshness.CURRENT,LiveNavigationSnapshot.from(cold.copy(observedThisSession=true),true,2000).freshness)
        assertEquals(LiveFreshness.STALE,LiveNavigationSnapshot.from(cold.copy(observedThisSession=true),false,2000).freshness)
        assertEquals(LiveFreshness.STALE,LiveNavigationSnapshot.from(cold.copy(observedThisSession=true),true,400000).freshness)
        assertEquals(LiveFreshness.REMOVED,LiveNavigationSnapshot.from(cold.copy(saved=saved.copy(status=ObservationStatus.REMOVED)),true,2000).freshness)
    }
    @Test fun storedObservationRoundTripsWithoutInventingRouteFields() {
        val saved=SavedObservation(MapsNotificationFilter.extract(raw())!!,ObservationStatus.ACTIVE,1000,1000)
        assertEquals(saved,SavedObservationCodec.decode(SavedObservationCodec.encode(saved)))
        assertNull(SavedObservationCodec.decode("bad payload"))
    }
}
