package app.manzil.offline

import android.content.Context
import app.manzil.offline.routing.RoutingPackIntegrity
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.Locale

 data class Place(val name:String,val latitude:Double,val longitude:Double,val kind:String="pin",val aliases:String="")
class OfflinePack(context:Context) {
 val root=File(context.filesDir,"manzil")
 val map=File(root,"offline-map")
 val routing=File(root,"offline-routing")
 fun ready()=File(routing,"profiles/walking.brf").isFile && File(routing,"profiles/lookups.dat").isFile && mapReady() && listOf("E75_N10.rd5","E80_N10.rd5").all { RoutingPackIntegrity.issue(File(routing,"segments/$it"))==null }
 private fun mapReady():Boolean=runCatching {
  val manifest=JSONObject(File(map,"manifest.json").readText())
  val files=manifest.getJSONObject("files")
  val base=map.canonicalPath+File.separator
  fun complete(path:String,bytes:Long):Boolean { val f=File(map,path);return f.canonicalPath.startsWith(base) && f.isFile && f.length()==bytes && bytes>0 }
  require(files.keys().asSequence().all { complete(it,files.getJSONObject(it).getLong("bytes")) })
  val tiles=manifest.getJSONArray("tiles");require(tiles.length()>0)
  (0 until tiles.length()).all { val tile=tiles.getJSONObject(it);complete(tile.getString("file"),tile.getLong("bytes")) }
 }.getOrDefault(false)
 fun inCoverage(lat:Double,lon:Double)=lat in 12.65..13.30 && lon in 79.95..80.35
 fun places():List<Place> {
  val f=File(map,"search.json");if(!f.isFile)return emptyList()
  val items=JSONObject(f.readText()).getJSONArray("entries")
  return (0 until items.length()).map { val p=items.getJSONObject(it);Place(p.getString("name"),p.getDouble("lat"),p.getDouble("lon"),p.optString("kind"),p.optJSONArray("aliases")?.toString().orEmpty()) }
 }
 companion object { fun normalized(s:String)=Normalizer.normalize(s,Normalizer.Form.NFKD).lowercase(Locale.ROOT).trim() }
}
