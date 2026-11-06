package app.manzil.offline.routing

import btools.router.*
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.floor
import kotlin.math.roundToInt

/** WGS84 coordinates. */
data class RouteCoordinate(val latitude: Double, val longitude: Double) {
    init { require(latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0) }
}
data class OfflineTurn(val pointIndex: Int, val instruction: String, val command: Int, val exitNumber: Int, val distanceToNextMeters: Double)
/**
 * Route geometry + turns shown and followed by the app. Despite the historical name this also
 * carries Google Routes results; [source] says which engine produced it and [warnings] holds
 * provider notices that must be shown with the route.
 */
data class OfflineRoute(
    val points: List<RouteCoordinate>,
    val distanceMeters: Int,
    val estimatedSeconds: Int,
    val turns: List<OfflineTurn>,
    val calculationMillis: Long,
    val source: RouteSource = RouteSource.OFFLINE_BROUTER,
    val warnings: List<String> = emptyList(),
)
class OfflineRoutingException(message: String) : Exception(message)

/** Blocking on-device router. Call on Dispatchers.IO, never the main thread. One request per instance. */
class OfflineRouter(private val packDirectory: File) {
    private val active = AtomicReference<RoutingEngine?>(null)
    private val routeLock = Any()
    fun cancel() { active.get()?.terminate() }

    fun route(origin: RouteCoordinate, destination: RouteCoordinate, timeoutMs: Long = 20_000): OfflineRoute = synchronized(routeLock) {
        require(timeoutMs in 1000..120000)
        val segments = File(packDirectory, "segments")
        val profile = File(packDirectory, "profiles/walking.brf")
        if (!profile.isFile || !File(profile.parentFile, "lookups.dat").isFile) throw OfflineRoutingException("Walking profile is not installed. Download the Chennai pack first.")
        for(point in listOf(origin,destination)) {
            val file = File(segments, segmentName(point))
            RoutingPackIntegrity.issue(file)?.let { throw OfflineRoutingException("Offline routing pack unavailable: $it. Install the complete region pack.") }
        }
        val rc = RoutingContext().apply {
            localFunction = profile.absolutePath
            memoryclass = 128
            turnInstructionMode = 4
            waypointCatchingRange = 150.0
            keyValues = hashMapOf("turnInstructionMode" to "4", "footMode" to "1", "bikeMode" to "0", "carMode" to "0")
        }
        fun node(point: RouteCoordinate, label: String) = OsmNodeNamed().apply {
            name = label
            ilat = ((point.latitude + 90.0) * 1_000_000).roundToInt()
            ilon = ((point.longitude + 180.0) * 1_000_000).roundToInt()
        }
        val started = System.nanoTime()
        val engine = try { RoutingEngine(null, null, segments, arrayListOf(node(origin,"start"),node(destination,"destination")), rc) }
        catch(e: Exception) { throw OfflineRoutingException("Cannot load the offline walking pack: ${e.message}") }
        engine.quite = true
        active.set(engine)
        try {
            engine.doRun(timeoutMs)
            engine.errorMessage?.let { throw OfflineRoutingException("No offline walking route: $it") }
            val track = engine.foundTrack ?: throw OfflineRoutingException("The router returned no route.")
            if(track.nodes.size < 2) throw OfflineRoutingException("No connected walking path was found between these points.")
            val points = track.nodes.map { RouteCoordinate(it.iLat/1_000_000.0 - 90.0,it.iLon/1_000_000.0 - 180.0) }
            val turns = ManzilTurnAccess.turns(track).filter { it.pointIndex in points.indices }.map {
                OfflineTurn(it.pointIndex, instruction(it.command,it.exitNumber),it.command,it.exitNumber,it.distanceToNextMeters)
            }
            // A plain walking estimate at 4.5km/h, explicitly not live ETA; no bicycle speed model.
            OfflineRoute(points,track.distance,(track.distance / 1.25).roundToInt(),turns,(System.nanoTime()-started)/1_000_000)
        } catch(e: OfflineRoutingException) { throw e }
        catch(e: Exception) { throw OfflineRoutingException("Offline route calculation failed: ${e.message}") }
        finally { active.compareAndSet(engine,null) }
    }
    companion object {
        fun segmentName(point: RouteCoordinate): String {
            val lon=floor(point.longitude/5).toInt()*5
            val lat=floor(point.latitude/5).toInt()*5
            return "${if(lon<0) "W" else "E"}${kotlin.math.abs(lon)}_${if(lat<0) "S" else "N"}${kotlin.math.abs(lat)}.rd5"
        }
        fun instruction(command: Int, exit: Int): String = when(command) {
            1 -> "Continue straight"
            2 -> "Turn left"
            3 -> "Bear left"
            4 -> "Turn sharply left"
            5 -> "Turn right"
            6 -> "Bear right"
            7 -> "Turn sharply right"
            8 -> "Keep left"
            9 -> "Keep right"
            10,11,15 -> "Make a U-turn"
            13,14 -> if(exit != 0) "At the roundabout, take exit ${kotlin.math.abs(exit)}" else "Continue through the roundabout"
            12 -> "Off the mapped route"
            16 -> "Unmapped straight-line connection"
            17 -> "Take the left exit"
            18 -> "Take the right exit"
            100 -> "Arrive at destination"
            else -> "Follow the highlighted walking route"
        }
    }
}

