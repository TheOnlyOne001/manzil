package app.manzil.offline

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

enum class HazardLayer(val label:String,val wire:String) {
 NONE("Map only",""), NIGHT("Night Walk","night"), HISTORY("Flood context","history"), RISK("Modelled flood risk","risk"), REPORTS("Recent reports","reports"), INFRASTRUCTURE("Elevated roads & transit","infrastructure")
}
data class HazardPoint(val latitude:Double,val longitude:Double)
data class HazardFeature(val id:String,val title:String,val layer:HazardLayer,val evidence:String,val detail:String,val source:String,val point:HazardPoint,val road:List<HazardPoint>,val observedAt:Instant?,val expiresAt:Instant?,val kind:String="",val historicalDepthCm:Double?=null,val riskBand:String="",val supportKind:String="",val openingHours:String="") {
 fun visibleAt(now:Instant)=(layer!=HazardLayer.REPORTS && layer!=HazardLayer.RISK) || (observedAt!=null && expiresAt!=null && !observedAt.isAfter(now) && expiresAt.isAfter(now))
}
object HazardParser {
 fun parse(text:String,now:Instant=Instant.now()):List<HazardFeature> {
  val root=JSONObject(text);require(root.getString("type")=="FeatureCollection")
  val features=root.getJSONArray("features");require(features.length()<=10000)
  fun point(a:JSONArray):HazardPoint {val lon=a.getDouble(0);val lat=a.getDouble(1);require(lat in -90.0..90.0 && lon in -180.0..180.0);return HazardPoint(lat,lon)}
  fun instant(p:JSONObject,key:String)=runCatching{Instant.parse(p.getString(key))}.getOrNull()
  return (0 until features.length()).mapNotNull { index->runCatching {
   val f=features.getJSONObject(index);val p=f.getJSONObject("properties");val g=f.getJSONObject("geometry")
   if(g.getString("type")!="Point")return@runCatching null
   val layer=HazardLayer.entries.firstOrNull{it!=HazardLayer.NONE && it.wire==p.optString("layer")}?:return@runCatching null
   val evidence=p.getString("evidence")
   if((layer==HazardLayer.NIGHT && (evidence!="mapped" || p.optString("kind") !in listOf("night_lit","night_unlit","night_unknown","night_support"))) || (layer==HazardLayer.HISTORY && evidence!="historical") || (layer==HazardLayer.REPORTS && evidence!="reported") || (layer==HazardLayer.RISK && evidence!="modelled") || (layer==HazardLayer.INFRASTRUCTURE && evidence!="curated"))return@runCatching null
   if(layer==HazardLayer.RISK && p.optString("risk_band") !in listOf("watch","elevated","high"))return@runCatching null
   val road=p.optJSONObject("road_context")?.takeIf{it.optBoolean("approximate") && it.optString("type")=="LineString"}?.optJSONArray("coordinates")
   HazardFeature(p.getString("id"),p.getString("title"),layer,evidence,p.optString("detail"),p.optString("source"),point(g.getJSONArray("coordinates")),road?.let{r->(0 until r.length()).map{point(r.getJSONArray(it))}}.orEmpty(),instant(p,"observed_at"),instant(p,"expires_at"),p.optString("kind"),p.optDouble("historical_depth_cm").takeIf{layer==HazardLayer.HISTORY && p.optString("kind")=="survey_point" && it.isFinite() && it>=0},p.optString("risk_band"),p.optString("support_kind"),p.optString("opening_hours"))
  }.getOrNull() }.filter{it.visibleAt(now)}
 }
}
data class HazardSnapshot(val features:List<HazardFeature> = emptyList(),val fetchedAt:Instant?=null,val saved:Boolean=true,val message:String="Saved layers",val riskBelowTriggerUntil:Instant?=null)
class HazardRepository(context:Context,private val root:File) {
 private fun belowTrigger(text:String):Instant?=runCatching {
  val risk=JSONObject(text).optJSONObject("metadata")?.optJSONObject("modelled_risk")?:return@runCatching null
  if(risk.optString("status")=="available" && risk.optString("reason")=="rain_below_watch_threshold") Instant.parse(risk.getString("expires_at")) else null
 }.getOrNull()

 // Static directory points remain available when an older live/cache feed lacks them.
 private fun withBundledContext(features:List<HazardFeature>):List<HazardFeature> {
  val directory=runCatching{HazardParser.parse(File(root,"overlays/chennai_map_overlays.geojson").readText()).filter{(it.layer==HazardLayer.NIGHT && it.supportKind=="police" && it.title.contains("women",true)) || it.id.startsWith("flood2015-")}}.getOrDefault(emptyList())
  return (features+directory).distinctBy{it.id}
 }
 private val cache=AtomicFile(File(context.filesDir,"map-overlays-cache.geojson"))
 fun saved():HazardSnapshot {
  val cached=runCatching{cache.openRead().bufferedReader().use{it.readText()}}.getOrNull()
  if(cached!=null && cached.contains("historical_depth_cm") && cached.contains("night_lit"))runCatching{withBundledContext(HazardParser.parse(cached))}.getOrNull()?.let{return HazardSnapshot(it,Instant.ofEpochMilli(cache.baseFile.lastModified()),true,"Saved layers",belowTrigger(cached))}
  val bundled=File(root,"overlays/chennai_map_overlays.geojson")
  return if(bundled.isFile)bundled.readText().let{HazardSnapshot(HazardParser.parse(it),null,true,"Saved layers",belowTrigger(it))} else HazardSnapshot(message="Layers unavailable")
 }
 fun fetch(base:String):HazardSnapshot {
  val url=URL(base.trimEnd('/')+"/api/v1/map-overlays/chennai")
  require(url.protocol=="https" || (url.protocol=="http" && url.host in listOf("127.0.0.1","localhost")))
  val connection=url.openConnection() as HttpURLConnection
  val text=try {connection.connectTimeout=4000;connection.readTimeout=10000;connection.setRequestProperty("Accept","application/geo+json, application/json")
   check(connection.responseCode==200){"Layer service unavailable"}
   connection.inputStream.use {input->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val n=input.read(buffer);if(n<0)break;check(out.size()+n<=5_000_000);out.write(buffer,0,n)};out.toString("UTF-8")}
  }finally{connection.disconnect()}
  val features=withBundledContext(HazardParser.parse(text))
  val out=cache.startWrite();try{out.write(text.toByteArray());cache.finishWrite(out)}catch(e:Exception){cache.failWrite(out);throw e}
  return HazardSnapshot(features,Instant.now(),false,"Synced layers",belowTrigger(text))
 }
}
