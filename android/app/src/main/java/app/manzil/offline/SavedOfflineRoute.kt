package app.manzil.offline

import android.util.AtomicFile
import app.manzil.offline.routing.*
import app.manzil.offline.location.RoutePoint
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Persists actual engine geometry and turns. Restored routes always resume paused awaiting fresh GPS. */
class SavedOfflineRoute(directory:File) {
 private val file=AtomicFile(File(directory,"offline-route.json"))
 data class Saved(val destination:Place,val route:OfflineRoute,val manualOrigin:RoutePoint?=null)
 @Synchronized fun clear()=file.delete()
 @Synchronized fun save(destination:Place,route:OfflineRoute,manualOrigin:RoutePoint?=null){
  val j=JSONObject().put("version",1).put("destination",JSONObject().put("name",destination.name).put("lat",destination.latitude).put("lon",destination.longitude))
   .put("distance",route.distanceMeters).put("seconds",route.estimatedSeconds).put("calculationMillis",route.calculationMillis)
   .put("points",JSONArray().apply{route.points.forEach{put(JSONArray().put(it.latitude).put(it.longitude))}})
   .put("turns",JSONArray().apply{route.turns.forEach{put(JSONObject().put("index",it.pointIndex).put("instruction",it.instruction).put("command",it.command).put("exit",it.exitNumber).put("distance",it.distanceToNextMeters))}})
  manualOrigin?.let {j.put("manualOrigin",JSONArray().put(it.latitude).put(it.longitude))}
  val output=file.startWrite();try{output.write(j.toString().toByteArray(Charsets.UTF_8));file.finishWrite(output)}catch(e:Exception){file.failWrite(output);throw e}
 }
 @Synchronized fun load():Saved?=runCatching {
  val j=JSONObject(file.openRead().bufferedReader().use{it.readText()});require(j.getInt("version")==1)
  val d=j.getJSONObject("destination");val points=j.getJSONArray("points");val turns=j.getJSONArray("turns")
  val geometry=(0 until points.length()).map{val p=points.getJSONArray(it);RouteCoordinate(p.getDouble(0),p.getDouble(1))};require(geometry.size>=2)
  val hints=(0 until turns.length()).map{val t=turns.getJSONObject(it);OfflineTurn(t.getInt("index"),t.getString("instruction"),t.getInt("command"),t.getInt("exit"),t.getDouble("distance"))};require(hints.all{it.pointIndex in geometry.indices})
  val manual=j.optJSONArray("manualOrigin")?.let{RoutePoint(it.getDouble(0),it.getDouble(1))}
  Saved(Place(d.getString("name"),d.getDouble("lat"),d.getDouble("lon")),OfflineRoute(geometry,j.getInt("distance"),j.getInt("seconds"),hints,j.getLong("calculationMillis")),manual)
 }.getOrNull()
}
