package app.manzil.offline.location

import kotlin.math.*

data class RoutePoint(val latitude: Double, val longitude: Double) {
    init { require(latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0) }
}

data class NavigationFix(
    val point: RoutePoint,
    val accuracyMeters: Double,
    val elapsedRealtimeMillis: Long,
    val cached: Boolean = false,
)

enum class TrackingQuality { WAITING, TRACKING, STALE_FIX, POOR_ACCURACY, UNCERTAIN_MATCH, OFF_ROUTE, ARRIVED }

data class RouteProgress(
    val quality: TrackingQuality,
    val snappedPoint: RoutePoint?,
    val segmentIndex: Int?,
    val traveledMeters: Double,
    val remainingMeters: Double,
    val distanceFromRouteMeters: Double?,
    val consecutiveOffRouteFixes: Int,
    /** True after three independent accurate fresh fixes outside the route corridor. */
    val shouldReroute: Boolean,
)

/** Pure local geometry. No network, synthetic position or extrapolated movement. */
object RouteGeometry {
    private const val EARTH = 6_371_008.8
    fun distance(a: RoutePoint, b: RoutePoint): Double {
        val lat1 = Math.toRadians(a.latitude); val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1; val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
    data class Projection(val point: RoutePoint, val fraction: Double, val distanceMeters: Double)
    fun project(point: RoutePoint, a: RoutePoint, b: RoutePoint): Projection {
        // Equirectangular projection around this short route segment, suitable for local road geometry.
        val latitudeScale = Math.PI * EARTH / 180.0
        val longitudeScale = latitudeScale * cos(Math.toRadians((a.latitude + b.latitude) / 2))
        val bx = (b.longitude - a.longitude) * longitudeScale
        val by = (b.latitude - a.latitude) * latitudeScale
        val px = (point.longitude - a.longitude) * longitudeScale
        val py = (point.latitude - a.latitude) * latitudeScale
        val length2 = bx * bx + by * by
        val t = if (length2 < 0.0001) 0.0 else ((px * bx + py * by) / length2).coerceIn(0.0, 1.0)
        val projected = RoutePoint(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
        return Projection(projected, t, distance(point, projected))
    }
}

/**
 * One instance per calculated route. Accuracy-aware local route matching with temporal continuity.
 * An ambiguous jump along a loop/crossing does not silently advance progress. Reset by constructing
 * a new instance after rerouting. Feed each GPS fix once; duplicate callbacks cannot satisfy hysteresis.
 */
class RouteProgressTracker(
    private val points: List<RoutePoint>,
    private val maximumFixAgeMs: Long = 15_000L,
    private val maximumAccuracyMeters: Double = 30.0,
) {
    init { require(points.size >= 2) }
    private val lengths = DoubleArray(points.size - 1) { RouteGeometry.distance(points[it], points[it + 1]) }
    private val cumulative = DoubleArray(points.size).also { result -> for (i in lengths.indices) result[i + 1] = result[i] + lengths[i] }
    val totalMeters: Double = cumulative.last()
    private var lastAcceptedAlong: Double? = null
    private var lastAcceptedAt: Long? = null
    private var lastProcessedAt: Long? = null
    private var offRouteCount = 0
    private var arrivalCount = 0
    private var last = RouteProgress(TrackingQuality.WAITING, null, null, 0.0, totalMeters, null, 0, false)

    private data class Candidate(val projection: RouteGeometry.Projection, val segment: Int, val along: Double)

    fun update(fix: NavigationFix, nowElapsedRealtimeMillis: Long): RouteProgress {
        val age = nowElapsedRealtimeMillis - fix.elapsedRealtimeMillis
        if (fix.cached || age !in 0..maximumFixAgeMs) return invalid(TrackingQuality.STALE_FIX)
        if (!fix.accuracyMeters.isFinite() || fix.accuracyMeters <= 0 || fix.accuracyMeters > maximumAccuracyMeters) return invalid(TrackingQuality.POOR_ACCURACY)
        if (lastProcessedAt != null && fix.elapsedRealtimeMillis <= lastProcessedAt!!) return last
        if (lastProcessedAt != null && fix.elapsedRealtimeMillis - lastProcessedAt!! > maximumFixAgeMs) { offRouteCount = 0; arrivalCount = 0 }
        lastProcessedAt = fix.elapsedRealtimeMillis
        val candidates = lengths.indices.map { index ->
            val p = RouteGeometry.project(fix.point, points[index], points[index + 1])
            Candidate(p, index, cumulative[index] + lengths[index] * p.fraction)
        }
        val global = candidates.minWith(compareBy<Candidate> { it.projection.distanceMeters }.thenBy { it.along })
        val corridor = max(30.0, fix.accuracyMeters * 2.0)
        if (global.projection.distanceMeters > corridor) {
            offRouteCount += 1; arrivalCount = 0
            last = last.copy(quality = if (offRouteCount >= 3) TrackingQuality.OFF_ROUTE else TrackingQuality.UNCERTAIN_MATCH,
                distanceFromRouteMeters = global.projection.distanceMeters, consecutiveOffRouteFixes = offRouteCount, shouldReroute = offRouteCount >= 3)
            return last
        }
        offRouteCount = 0
        val previous = lastAcceptedAlong
        val elapsed = lastAcceptedAt?.let { ((fix.elapsedRealtimeMillis - it).coerceAtLeast(0) / 1000.0) } ?: 0.0
        // Walking speed bound + generous sensor corridor; capped to avoid leaps after long gaps.
        val maxAlongChange = min(150.0, 4.0 * elapsed + max(35.0, fix.accuracyMeters * 2))
        val continuous = if (previous == null) candidates else candidates.filter { abs(it.along - previous) <= maxAlongChange }
        val match = continuous.filter { it.projection.distanceMeters <= corridor }
            .minWithOrNull(compareBy<Candidate> { it.projection.distanceMeters }.thenBy { if (previous == null) it.along else abs(it.along - previous) })
            ?: return invalid(TrackingQuality.UNCERTAIN_MATCH)
        lastAcceptedAlong = match.along; lastAcceptedAt = fix.elapsedRealtimeMillis
        val remaining = (totalMeters - match.along).coerceAtLeast(0.0)
        val endpointDistance = RouteGeometry.distance(fix.point, points.last())
        if (remaining <= 20.0 && endpointDistance + fix.accuracyMeters <= 25.0) arrivalCount++ else arrivalCount = 0
        last = RouteProgress(if (arrivalCount >= 2) TrackingQuality.ARRIVED else TrackingQuality.TRACKING,
            match.projection.point, match.segment, match.along, remaining, match.projection.distanceMeters, 0, false)
        return last
    }

    /** Timer callers can invalidate a formerly fresh fix instead of continuing to show it as current. */
    fun invalidateFix(quality: TrackingQuality = TrackingQuality.STALE_FIX): RouteProgress = invalid(quality)

    private fun invalid(quality: TrackingQuality): RouteProgress {
        offRouteCount = 0; arrivalCount = 0
        last = last.copy(quality = quality, consecutiveOffRouteFixes = 0, shouldReroute = false)
        return last
    }
}
