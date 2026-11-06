package app.manzil.offline.search

import android.content.Context
import app.manzil.offline.Place
import app.manzil.offline.OfflinePack
import app.manzil.offline.location.RoutePoint
import app.manzil.offline.location.RouteGeometry
import app.manzil.offline.routing.GoogleRoutesCredentials
import app.manzil.system.NetworkStatus
import app.manzil.system.currentNetworkStatus
import kotlinx.coroutines.*

data class PlaceSearchResult(val query:String="",val items:List<PlaceCandidate> = emptyList(),val online:Boolean=false,val message:String?=null)

class PlaceSearchRepository(context:Context) {
 private val context=context.applicationContext
 private val client by lazy { GoogleRoutesCredentials.fromManifest(this.context)?.let{GooglePlacesClient(it)} }
 suspend fun search(query:String,saved:List<Place>,bias:RoutePoint?):PlaceSearchResult {
  val q=query.trim().take(200)
  if(q.length<2)return PlaceSearchResult(query=q)
  var failure:String?=null
  if(currentNetworkStatus(context)==NetworkStatus.ONLINE) {
   try {
    val service=withContext(Dispatchers.IO){client}
    if(service!=null){
     val found=service.search(q,bias?.latitude,bias?.longitude)
     currentCoroutineContext().ensureActive()
     if(found.isNotEmpty())return PlaceSearchResult(q,found,true)
     failure="No Google Places match. Try a nearby landmark or a more specific name."
    }else failure="Online place search is not configured. Showing saved places."
   }catch(e:CancellationException){throw e}
   catch(e:Exception){failure="Online search unavailable. Showing saved places; you can retry."}
  }
  val local=withContext(Dispatchers.Default){
   val words=OfflinePack.normalized(q).split(Regex("\\s+")).filter{it.isNotBlank()}
   saved.filter{p->val name=OfflinePack.normalized(p.name+" "+p.aliases);words.all{name.contains(it)}}
    .sortedBy{if(bias==null)0.0 else RouteGeometry.distance(bias,RoutePoint(it.latitude,it.longitude))}.take(8)
    .map{PlaceCandidate("saved:${it.latitude}:${it.longitude}:${it.name}",it.name,it.kind,it.latitude,it.longitude)}
  }
  return PlaceSearchResult(q,local,false,failure?:if(local.isEmpty())"No saved match. Try another name or choose on the map." else null)
 }
}

fun PlaceCandidate.asPlace()=Place(name,latitude,longitude,"place",address)
