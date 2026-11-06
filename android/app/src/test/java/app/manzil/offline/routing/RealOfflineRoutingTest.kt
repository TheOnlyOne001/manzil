package app.manzil.offline.routing

import app.manzil.offline.location.RouteGeometry
import app.manzil.offline.location.RoutePoint
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Integration tests against the same downloaded, checksummed graph shipped in the APK. */
class RealOfflineRoutingTest {
    private val directory = File(System.getProperty("manzil.routing.dir"))
    private fun verify(a: RouteCoordinate, b: RouteCoordinate) {
        val route = OfflineRouter(directory).route(a, b, 60000)
        assertTrue("Expected actual road geometry", route.points.size > 5)
        assertTrue(route.distanceMeters > 100)
        assertTrue(route.turns.isNotEmpty())
        assertTrue(route.turns.all { it.pointIndex in route.points.indices })
        fun distance(x: RouteCoordinate, y: RouteCoordinate) = RouteGeometry.distance(RoutePoint(x.latitude,x.longitude),RoutePoint(y.latitude,y.longitude))
        assertTrue("Start snapped too far away", distance(a,route.points.first()) <= 151)
        assertTrue("Destination snapped too far away", distance(b,route.points.last()) <= 151)
    }
    @Test fun potheriUsesEasternGraph() = verify(RouteCoordinate(12.823,80.045),RouteCoordinate(12.831,80.042))
    @Test fun chengalpattuUsesWesternGraph() = verify(RouteCoordinate(12.693351,79.98093),RouteCoordinate(12.697092,79.976565))
    @Test fun veppampattuCrossesGraphBoundary() = verify(RouteCoordinate(13.123725,79.998437),RouteCoordinate(13.125815,80.010147))
}
