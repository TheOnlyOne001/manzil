package app.manzil.offline.routing

import android.util.AtomicFile
import app.manzil.offline.Place
import app.manzil.offline.location.RoutePoint
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A persisted route. Restored routes always resume paused, awaiting a fresh GPS fix. */
data class SavedRoute(val destination: Place, val route: OfflineRoute, val manualOrigin: RoutePoint? = null)

/**
 * Same on-disk format (version 1, `offline-route.json`) as the original SavedOfflineRoute, plus two
 * optional keys: `source` and `warnings`. Files written by older builds have neither and decode as
 * BRouter routes; older builds reading new files ignore the extra keys.
 */
object SavedRouteCodec {
    const val VERSION = 1

    fun encode(saved: SavedRoute): String {
        val route = saved.route
        val d = saved.destination
        val j = JSONObject().put("version", VERSION)
            .put("destination", JSONObject().put("name", d.name).put("lat", d.latitude).put("lon", d.longitude))
            .put("distance", route.distanceMeters).put("seconds", route.estimatedSeconds)
            .put("calculationMillis", route.calculationMillis)
            .put("source", route.source.storageKey)
            .put("warnings", JSONArray().apply { route.warnings.forEach { put(it) } })
            .put("points", JSONArray().apply { route.points.forEach { put(JSONArray().put(it.latitude).put(it.longitude)) } })
            .put("turns", JSONArray().apply {
                route.turns.forEach {
                    put(JSONObject().put("index", it.pointIndex).put("instruction", it.instruction).put("command", it.command)
                        .put("exit", it.exitNumber).put("distance", it.distanceToNextMeters))
                }
            })
        saved.manualOrigin?.let { j.put("manualOrigin", JSONArray().put(it.latitude).put(it.longitude)) }
        return j.toString()
    }

    fun decode(text: String): SavedRoute? = runCatching {
        val j = JSONObject(text)
        require(j.getInt("version") == VERSION)
        val d = j.getJSONObject("destination")
        val points = j.getJSONArray("points")
        val turns = j.getJSONArray("turns")
        val geometry = (0 until points.length()).map { val p = points.getJSONArray(it); RouteCoordinate(p.getDouble(0), p.getDouble(1)) }
        require(geometry.size >= 2)
        val hints = (0 until turns.length()).map {
            val t = turns.getJSONObject(it)
            OfflineTurn(t.getInt("index"), t.getString("instruction"), t.getInt("command"), t.getInt("exit"), t.getDouble("distance"))
        }
        require(hints.all { it.pointIndex in geometry.indices })
        val source = RouteSource.fromStorage(j.optString("source", ""))
        val stored = j.optJSONArray("warnings")?.let { w ->
            (0 until w.length()).mapNotNull { w.optString(it, null)?.takeIf(String::isNotBlank) }
        }.orEmpty()
        // The Google walking notice must accompany a restored Google route even if a file lost it.
        val warnings = if (source == RouteSource.GOOGLE_ROUTES && GOOGLE_WALKING_BETA_WARNING !in stored) {
            listOf(GOOGLE_WALKING_BETA_WARNING) + stored
        } else stored
        val manual = j.optJSONArray("manualOrigin")?.let { RoutePoint(it.getDouble(0), it.getDouble(1)) }
        SavedRoute(
            Place(d.getString("name"), d.getDouble("lat"), d.getDouble("lon")),
            OfflineRoute(geometry, j.getInt("distance"), j.getInt("seconds"), hints, j.getLong("calculationMillis"), source, warnings),
            manual,
        )
    }.getOrNull()
}

/** Atomic file wrapper; replaces SavedOfflineRoute for the ViewModel. Call off the main thread. */
class SavedRouteStore(directory: File) {
    private val file = AtomicFile(File(directory, "offline-route.json"))

    @Synchronized fun clear() = file.delete()

    @Synchronized fun save(saved: SavedRoute) {
        val output = file.startWrite()
        try {
            output.write(SavedRouteCodec.encode(saved).toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (e: Exception) {
            file.failWrite(output)
            throw e
        }
    }

    @Synchronized fun load(): SavedRoute? =
        runCatching { file.openRead().bufferedReader().use { it.readText() } }.getOrNull()?.let(SavedRouteCodec::decode)
}
