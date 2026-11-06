package app.manzil.offline.routing

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToLong

class GoogleRoutesProtocolTest {
    private fun encode(points: List<RouteCoordinate>): String {
        val out = StringBuilder()
        var lastLat = 0L
        var lastLon = 0L
        fun put(value: Long) {
            var v = if (value < 0) (value shl 1).inv() else value shl 1
            while (v >= 0x20) { out.append(((0x20 or (v and 0x1f).toInt()) + 63).toChar()); v = v shr 5 }
            out.append((v.toInt() + 63).toChar())
        }
        for (p in points) {
            val lat = (p.latitude * 1e5).roundToLong()
            val lon = (p.longitude * 1e5).roundToLong()
            put(lat - lastLat); put(lon - lastLon)
            lastLat = lat; lastLon = lon
        }
        return out.toString()
    }

    private val a = RouteCoordinate(13.08268, 80.27072)
    private val b = RouteCoordinate(13.08300, 80.27100)
    private val c = RouteCoordinate(13.08410, 80.27105)
    private val d = RouteCoordinate(13.08420, 80.27210)

    private fun step(points: List<RouteCoordinate>?, maneuver: String?, text: String?, meters: Int?) = JSONObject().apply {
        points?.let { put("polyline", JSONObject().put("encodedPolyline", encode(it))) }
        meters?.let { put("distanceMeters", it) }
        put("startLocation", JSONObject().put("latLng", JSONObject().put("latitude", (points ?: listOf(a)).first().latitude).put("longitude", (points ?: listOf(a)).first().longitude)))
        if (maneuver != null || text != null) put("navigationInstruction", JSONObject().apply {
            maneuver?.let { put("maneuver", it) }; text?.let { put("instructions", it) }
        })
    }

    private fun response(steps: List<JSONObject>, extra: JSONObject.() -> Unit = {}) = JSONObject().put(
        "routes", JSONArray().put(JSONObject().put("distanceMeters", 245).put("duration", "196s")
            .put("polyline", JSONObject().put("encodedPolyline", encode(listOf(a, b, c, d))))
            .put("legs", JSONArray().put(JSONObject().put("steps", JSONArray(steps)))).apply(extra)),
    ).toString()

    @Test fun decodesGoogleReferencePolyline() {
        // Example from Google's Encoded Polyline Algorithm Format documentation.
        val points = PolylineCodec.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(listOf(RouteCoordinate(38.5, -120.2), RouteCoordinate(40.7, -120.95), RouteCoordinate(43.252, -126.453)), points)
    }

    @Test fun truncatedPolylineIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { PolylineCodec.decode("_p~iF~ps|U_ulL") }
    }

    @Test fun stepsBecomeContinuousGeometryWithAlignedTurns() {
        val body = response(listOf(
            step(listOf(a, b), "DEPART", "Head northeast on Anna Salai", 40),
            step(listOf(b, c), "TURN_LEFT", "Turn left onto Wallajah Rd", 120),
            step(listOf(c, d), "TURN_SLIGHT_RIGHT", null, 85),
        )) { put("warnings", JSONArray().put("Use caution – this route may include steps.")) }
        val route = GoogleRoutesProtocol.parse(body, 321)

        assertEquals(RouteSource.GOOGLE_ROUTES, route.source)
        assertEquals("Shared junction points are not duplicated", listOf(a, b, c, d), route.points)
        assertEquals(245, route.distanceMeters)
        assertEquals(196, route.estimatedSeconds)
        assertEquals(321L, route.calculationMillis)
        assertEquals(listOf(0, 1, 2), route.turns.map { it.pointIndex })
        assertEquals(listOf(1, 2, 6), route.turns.map { it.command })
        assertEquals("Turn left onto Wallajah Rd", route.turns[1].instruction)
        assertEquals("Missing text falls back to the command phrase", "Bear right", route.turns[2].instruction)
        assertEquals(120.0, route.turns[1].distanceToNextMeters, 0.0)
        assertEquals(GOOGLE_WALKING_BETA_WARNING, route.warnings.first())
        assertTrue(route.warnings.contains("Use caution – this route may include steps."))
    }

    @Test fun overviewPolylineUsedWhenStepsHaveNoGeometry() {
        val body = response(listOf(step(null, "DEPART", "Head north", 40)).also {
            it[0].put("startLocation", JSONObject().put("latLng", JSONObject().put("latitude", a.latitude).put("longitude", a.longitude)))
        } + step(null, "TURN_RIGHT", "Turn right", 100).also {
            it.put("startLocation", JSONObject().put("latLng", JSONObject().put("latitude", c.latitude + 0.00001).put("longitude", c.longitude)))
        })
        val route = GoogleRoutesProtocol.parse(body, 1)
        assertEquals(listOf(a, b, c, d), route.points)
        assertEquals("Turn snaps to the nearest overview vertex", listOf(0, 2), route.turns.map { it.pointIndex })
    }

    @Test fun missingDurationUsesWalkingEstimate() {
        val body = JSONObject(response(listOf(step(listOf(a, b, c, d), "DEPART", "Go", 245)))).apply {
            getJSONArray("routes").getJSONObject(0).remove("duration")
        }.toString()
        assertEquals(196, GoogleRoutesProtocol.parse(body, 1).estimatedSeconds)
    }

    @Test fun emptyResponsesMeanNoRoute() {
        for (body in listOf("{}", "{\"routes\":[]}")) {
            val e = assertThrows(OnlineRoutingException::class.java) { GoogleRoutesProtocol.parse(body, 1) }
            assertEquals(OnlineFailureKind.NO_ROUTE, e.kind)
        }
    }

    @Test fun malformedBodiesAreReportedNotCrashed() {
        val bad = listOf(
            "not json",
            "{\"routes\":[{\"distanceMeters\":10}]}", // no geometry
            "{\"routes\":[{\"distanceMeters\":10,\"polyline\":{\"encodedPolyline\":\"_p~iF\"}}]}", // truncated
            "{\"routes\":[{\"polyline\":{\"encodedPolyline\":\"${encode(listOf(a, b))}\"}}]}", // no distance
        )
        for (body in bad) {
            val e = assertThrows(body, OnlineRoutingException::class.java) { GoogleRoutesProtocol.parse(body, 1) }
            assertEquals(body, OnlineFailureKind.MALFORMED, e.kind)
        }
    }

    @Test fun requestIsWalkingWithoutRoutingPreference() {
        val j = JSONObject(GoogleRoutesProtocol.requestBody(a, d))
        assertEquals("WALK", j.getString("travelMode"))
        assertFalse("routingPreference makes WALK requests fail", j.has("routingPreference"))
        assertEquals(a.latitude, j.getJSONObject("origin").getJSONObject("location").getJSONObject("latLng").getDouble("latitude"), 0.0)
        assertEquals(d.longitude, j.getJSONObject("destination").getJSONObject("location").getJSONObject("latLng").getDouble("longitude"), 0.0)
        assertFalse(j.getBoolean("computeAlternativeRoutes"))
        assertTrue(GoogleRoutesProtocol.ENDPOINT.startsWith("https://routes.googleapis.com/"))
        val mask = GoogleRoutesProtocol.FIELD_MASK.split(',')
        assertFalse("Wildcard masks are discouraged", mask.contains("*"))
        for (field in listOf("routes.distanceMeters", "routes.duration", "routes.warnings", "routes.legs.steps.navigationInstruction", "routes.legs.steps.polyline.encodedPolyline")) {
            assertTrue(field, mask.contains(field))
        }
    }

    @Test fun statusAndDurationMapping() {
        assertNull(GoogleRoutesProtocol.failureForStatus(200))
        assertEquals(OnlineFailureKind.KEY_REJECTED, GoogleRoutesProtocol.failureForStatus(403))
        assertEquals(OnlineFailureKind.KEY_REJECTED, GoogleRoutesProtocol.failureForStatus(401))
        assertEquals(OnlineFailureKind.QUOTA, GoogleRoutesProtocol.failureForStatus(429))
        assertEquals(OnlineFailureKind.BAD_REQUEST, GoogleRoutesProtocol.failureForStatus(400))
        assertEquals(OnlineFailureKind.SERVER, GoogleRoutesProtocol.failureForStatus(503))
        assertEquals(165, GoogleRoutesProtocol.parseDuration("165s"))
        assertEquals(4, GoogleRoutesProtocol.parseDuration("3.5s"))
        assertNull(GoogleRoutesProtocol.parseDuration("165"))
        assertNull(GoogleRoutesProtocol.parseDuration(""))
        assertEquals(13, GoogleRoutesProtocol.commandFor("ROUNDABOUT_LEFT"))
        assertEquals(0, GoogleRoutesProtocol.commandFor("FERRY"))
        assertEquals(100, GoogleRoutesProtocol.commandFor("DESTINATION"))
    }
}
