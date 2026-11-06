package app.manzil.offline.routing

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.roundToInt

/** Why an online request produced no usable route. Never carries credentials or request URLs. */
enum class OnlineFailureKind(val userText: String) {
    NOT_CONFIGURED("Google routing key is not configured"),
    TIMEOUT("Google did not answer in time"),
    NETWORK("Could not reach Google"),
    KEY_REJECTED("Google rejected the app's API key or Routes API is not enabled"),
    QUOTA("Google routing quota exceeded"),
    BAD_REQUEST("Google rejected the route request"),
    SERVER("Google routing service error"),
    NO_ROUTE("Google found no walking route"),
    MALFORMED("Google returned an unreadable route"),
}

class OnlineRoutingException(val kind: OnlineFailureKind, val httpStatus: Int? = null) :
    Exception(kind.userText + (httpStatus?.let { " (HTTP $it)" } ?: ""))

/**
 * Pure request/response mapping for `POST https://routes.googleapis.com/directions/v2:computeRoutes`
 * with travelMode WALK. No I/O here, so it is unit-testable on the JVM.
 */
object GoogleRoutesProtocol {
    const val ENDPOINT = "https://routes.googleapis.com/directions/v2:computeRoutes"

    /** Only what the app renders and follows; the field mask is mandatory for computeRoutes. */
    const val FIELD_MASK = "routes.distanceMeters,routes.duration,routes.staticDuration," +
        "routes.polyline.encodedPolyline,routes.warnings," +
        "routes.legs.steps.distanceMeters,routes.legs.steps.polyline.encodedPolyline," +
        "routes.legs.steps.startLocation,routes.legs.steps.navigationInstruction"

    /** Walking speed used only when Google omits a duration (same 4.5 km/h as BRouter). */
    private const val FALLBACK_WALK_MPS = 1.25

    fun requestBody(origin: RouteCoordinate, destination: RouteCoordinate, languageCode: String = "en"): String {
        fun waypoint(p: RouteCoordinate) = JSONObject().put(
            "location",
            JSONObject().put("latLng", JSONObject().put("latitude", p.latitude).put("longitude", p.longitude)),
        )
        // routingPreference must NOT be sent for WALK: the API fails the request.
        return JSONObject()
            .put("origin", waypoint(origin))
            .put("destination", waypoint(destination))
            .put("travelMode", "WALK")
            .put("computeAlternativeRoutes", false)
            .put("polylineQuality", "HIGH_QUALITY")
            .put("polylineEncoding", "ENCODED_POLYLINE")
            .put("units", "METRIC")
            .put("languageCode", languageCode)
            .toString()
    }

    /** Maps an HTTP status to a failure kind; 2xx returns null. */
    fun failureForStatus(status: Int): OnlineFailureKind? = when (status) {
        in 200..299 -> null
        401, 403 -> OnlineFailureKind.KEY_REJECTED
        429 -> OnlineFailureKind.QUOTA
        in 400..499 -> OnlineFailureKind.BAD_REQUEST
        else -> OnlineFailureKind.SERVER
    }

    /** Parses a 2xx body. Throws [OnlineRoutingException] for empty or malformed routes. */
    fun parse(body: String, calculationMillis: Long): OfflineRoute {
        try {
            val root = JSONObject(body)
            // An empty or absent `routes` means "no route could be found".
            val route = root.optJSONArray("routes")?.optJSONObject(0)
                ?: throw OnlineRoutingException(OnlineFailureKind.NO_ROUTE)
            val steps = route.optJSONArray("legs").objects().flatMap { it.optJSONArray("steps").objects() }

            val points = ArrayList<RouteCoordinate>()
            val stepStarts = ArrayList<Int>()
            for (step in steps) {
                val line = step.optJSONObject("polyline")?.optString("encodedPolyline").orEmpty()
                val decoded = if (line.isEmpty()) emptyList() else PolylineCodec.decode(line)
                stepStarts += if (decoded.isEmpty()) -1 else appendDedup(points, decoded)
            }
            if (points.size < 2) {
                // Steps had no geometry; use the overview polyline and place turns by start location.
                points.clear()
                route.optJSONObject("polyline")?.optString("encodedPolyline")?.takeIf { it.isNotEmpty() }
                    ?.let { points += PolylineCodec.decode(it) }
                stepStarts.clear()
                var cursor = 0
                for (step in steps) {
                    val start = step.optJSONObject("startLocation")?.optJSONObject("latLng")
                    if (start == null || points.isEmpty()) { stepStarts += -1; continue }
                    cursor = nearestIndexFrom(points, RouteCoordinate(start.getDouble("latitude"), start.getDouble("longitude")), cursor)
                    stepStarts += cursor
                }
            }
            if (points.size < 2) throw OnlineRoutingException(OnlineFailureKind.MALFORMED)

            val turns = steps.mapIndexedNotNull { i, step ->
                val index = stepStarts[i].takeIf { it in points.indices } ?: return@mapIndexedNotNull null
                val nav = step.optJSONObject("navigationInstruction")
                val command = commandFor(nav?.optString("maneuver"))
                val text = nav?.optString("instructions")?.trim().orEmpty()
                    .ifEmpty { OfflineRouter.instruction(command, 0) }
                OfflineTurn(index, text, command, 0, step.optDouble("distanceMeters", 0.0).takeIf { it.isFinite() } ?: 0.0)
            }

            val distance = route.optInt("distanceMeters", -1).takeIf { it > 0 }
                ?: steps.sumOf { it.optInt("distanceMeters", 0) }.takeIf { it > 0 }
                ?: throw OnlineRoutingException(OnlineFailureKind.MALFORMED)
            val seconds = parseDuration(route.optString("duration"))
                ?: parseDuration(route.optString("staticDuration"))
                ?: (distance / FALLBACK_WALK_MPS).roundToInt()
            val warnings = (listOf(GOOGLE_WALKING_BETA_WARNING) +
                route.optJSONArray("warnings").strings()).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            return OfflineRoute(points, distance, seconds, turns, calculationMillis, RouteSource.GOOGLE_ROUTES, warnings)
        } catch (e: OnlineRoutingException) {
            throw e
        } catch (e: JSONException) {
            throw OnlineRoutingException(OnlineFailureKind.MALFORMED)
        } catch (e: IllegalArgumentException) {
            // Polyline decoded to out-of-range coordinates, or truncated polyline.
            throw OnlineRoutingException(OnlineFailureKind.MALFORMED)
        }
    }

    /** Protobuf JSON Duration, e.g. "165s" or "3.5s". */
    fun parseDuration(value: String?): Int? {
        val text = value?.trim().orEmpty()
        if (!text.endsWith("s")) return null
        val seconds = text.dropLast(1).toDoubleOrNull() ?: return null
        return if (seconds.isFinite() && seconds >= 0) seconds.roundToInt() else null
    }

    /** Google maneuver → the BRouter command codes the app already renders. */
    fun commandFor(maneuver: String?): Int = when (maneuver) {
        "DEPART", "STRAIGHT", "NAME_CHANGE" -> 1
        "TURN_LEFT" -> 2
        "TURN_SLIGHT_LEFT" -> 3
        "TURN_SHARP_LEFT" -> 4
        "TURN_RIGHT" -> 5
        "TURN_SLIGHT_RIGHT" -> 6
        "TURN_SHARP_RIGHT" -> 7
        "FORK_LEFT", "RAMP_LEFT" -> 8
        "FORK_RIGHT", "RAMP_RIGHT" -> 9
        "UTURN_LEFT", "UTURN_RIGHT" -> 10
        "ROUNDABOUT_LEFT", "ROUNDABOUT_RIGHT" -> 13
        "DESTINATION" -> 100
        else -> 0 // MERGE, FERRY, unspecified: generic "follow the route"
    }

    /** Appends [segment] skipping a duplicated junction point; returns the index the segment starts at. */
    private fun appendDedup(points: MutableList<RouteCoordinate>, segment: List<RouteCoordinate>): Int {
        if (points.isNotEmpty() && points.last() == segment.first()) {
            points += segment.drop(1)
            return points.size - segment.size
        }
        points += segment
        return points.size - segment.size
    }

    private fun nearestIndexFrom(points: List<RouteCoordinate>, target: RouteCoordinate, from: Int): Int {
        var best = from
        var bestScore = Double.MAX_VALUE
        for (i in from until points.size) {
            val dLat = points[i].latitude - target.latitude
            val dLon = (points[i].longitude - target.longitude) * kotlin.math.cos(Math.toRadians(target.latitude))
            val score = dLat * dLat + dLon * dLon
            if (score < bestScore) { bestScore = score; best = i }
        }
        return best
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it, null) }
}

/** Google encoded polyline algorithm format, precision 1e5. */
object PolylineCodec {
    fun decode(encoded: String): List<RouteCoordinate> {
        val out = ArrayList<RouteCoordinate>()
        var index = 0
        var lat = 0L
        var lon = 0L
        fun next(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                require(index < encoded.length) { "Truncated polyline" }
                val b = encoded[index++].code - 63
                require(b in 0..63) { "Invalid polyline character" }
                result = result or ((b and 0x1f).toLong() shl shift)
                shift += 5
                require(shift <= 60) { "Polyline value too long" }
                if (b < 0x20) break
            }
            return if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        }
        while (index < encoded.length) {
            lat += next()
            lon += next()
            out += RouteCoordinate(lat / 1e5, lon / 1e5)
        }
        return out
    }
}
