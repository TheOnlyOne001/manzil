package app.manzil.offline

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.manzil.offline.location.*
import app.manzil.offline.routing.*
import app.manzil.system.currentNetworkStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.io.File

 data class OfflineUiState(val loading:Boolean=true,val packReady:Boolean=false,val waitingForLocation:Boolean=false,val places:List<Place> = emptyList(),val destination:Place?=null,val route:OfflineRoute?=null,val busy:Boolean=false,val message:String="Loading Chennai pack",val progress:RouteProgress?=null,val navigating:Boolean=false,val manualOrigin:RoutePoint?=null,val planningAccuracyMeters:Float?=null)
class OfflineViewModel(app:Application):AndroidViewModel(app) {
 val pack=OfflinePack(app)
 val locationSession=OfflineLocationSession.get(app)
 val gps=locationSession.gps
 private var pendingNavigation=false
 private val router=OfflineRouter(pack.routing)
 private val _ui=MutableStateFlow(OfflineUiState());val ui=_ui.asStateFlow()
 private var tracker:RouteProgressTracker?=null
 private var routeJob:Job?=null
 private var lastReroute=0L
 private val savedRoute=SavedRouteStore(app.filesDir)
 /** Google Routes client from the manifest key; null when unconfigured. Resolve on Dispatchers.IO. */
 private val googleRoutes:GoogleRoutesClient? by lazy { GoogleRoutesCredentials.fromManifest(app)?.let { GoogleRoutesClient(it) } }
 private val planner by lazy { RoutePlanner(
  online=OnlineRouteSource { o,d -> (googleRoutes ?: throw OnlineRoutingException(OnlineFailureKind.NOT_CONFIGURED)).route(o,d) },
  offline={ o,d -> withContext(Dispatchers.IO){router.route(o,d,60000)} },
 ) }
 private val prefs=app.getSharedPreferences("offline_navigation",0)
 init {
  viewModelScope.launch { OfflineNavigationLocationService.running.collect { running ->
   if(running && pendingNavigation) {pendingNavigation=false;_ui.update{it.copy(navigating=true,message=followingMessage())}}
   else if(!running && _ui.value.navigating) {_ui.update{it.copy(navigating=false,progress=null,message="Guidance stopped")}}
  } }
  viewModelScope.launch { locationSession.error.collect { error ->
   if(error!=null) {pendingNavigation=false;_ui.update{it.copy(navigating=false,message=error)}}
  } }

  viewModelScope.launch {
   val installation=withContext(Dispatchers.IO){runCatching{BundledOfflinePack.install(app,pack.root)}}
   if(installation.isFailure){_ui.update{it.copy(loading=false,message="Offline pack could not be prepared: ${installation.exceptionOrNull()?.message}")};return@launch}
   val places=withContext(Dispatchers.IO){runCatching {pack.places()}.getOrDefault(emptyList())}
   val saved=withContext(Dispatchers.IO){savedRoute.load()}
   val ready=withContext(Dispatchers.IO){pack.ready()}
   val dest=prefs.getString("destination",null)?.let {runCatching {val j=JSONObject(it);Place(j.getString("name"),j.getDouble("lat"),j.getDouble("lon"))}.getOrNull()}
   saved?.let {tracker=RouteProgressTracker(it.route.points.map{p->RoutePoint(p.latitude,p.longitude)})}
   _ui.update {it.copy(loading=false,packReady=ready,places=places,destination=saved?.destination?:dest,route=saved?.route,manualOrigin=saved?.manualOrigin,message=if(saved!=null) "Saved ${if(saved.route.source==RouteSource.GOOGLE_ROUTES)"Google walking" else "offline"} route • waiting for fresh GPS" else if(ready) "Chennai pack installed / works without internet" else "Chennai pack is not installed yet")}
  }
  viewModelScope.launch { gps.state.collect { state ->
   if(_ui.value.waitingForLocation && !_ui.value.loading && !_ui.value.busy){
    val f=state.fix
    if(state.status==GpsStatus.FIX && f!=null && RoutePreviewLocationPolicy.accepts(f.cached,f.ageMillis(),f.accuracyMeters)){calculate();return@collect}
   }
   val tracking=tracker ?: return@collect
   if(!_ui.value.navigating)return@collect
   val fix=state.fix
   val progress=if(state.status==GpsStatus.FIX && fix!=null) tracking.update(fix.forNavigation(),SystemClock.elapsedRealtime()) else tracking.invalidateFix()
   _ui.update {it.copy(progress=progress)}
   if(progress.shouldReroute && !_ui.value.busy && SystemClock.elapsedRealtime()-lastReroute>15000) {lastReroute=SystemClock.elapsedRealtime();calculate(true)}
  } }
 }
 fun select(place:Place) {stopLocationGuidance();savedRoute.clear();router.cancel();routeJob?.cancel();tracker=null;_ui.update {it.copy(waitingForLocation=false,destination=place,route=null,progress=null,navigating=false,busy=false,planningAccuracyMeters=null,message="Choose a start to calculate a walking route")};prefs.edit().putString("destination",JSONObject().put("name",place.name).put("lat",place.latitude).put("lon",place.longitude).toString()).apply()}
 fun origin(point:RoutePoint?) {stopLocationGuidance();router.cancel();routeJob?.cancel();savedRoute.clear();tracker=null;_ui.update {it.copy(waitingForLocation=false,busy=false,planningAccuracyMeters=null,manualOrigin=point,route=null,progress=null,navigating=false,message=if(point==null)"Using GPS location" else "Manual start / route preview only")}}
 fun calculate(reroute:Boolean=false) {
  val dest=_ui.value.destination?:return
  val state=gps.state.value
  val start=if(reroute)null else _ui.value.manualOrigin
  val fix=state.fix
  val origin=start ?: if(state.status==GpsStatus.FIX && fix!=null && RoutePreviewLocationPolicy.accepts(fix.cached,fix.ageMillis(),fix.accuracyMeters) && (!reroute || (fix.accuracyMeters?:999f)<=50)) fix.point else null
  if(origin==null){_ui.update {it.copy(waitingForLocation=!reroute,message="Waiting for a fresh, accurate GPS fix. Move near an open sky, or choose a start on the map.")};return}
  _ui.update{it.copy(waitingForLocation=false,planningAccuracyMeters=if(start==null)fix?.accuracyMeters else null)}
  val covered=pack.inCoverage(origin.latitude,origin.longitude)&&pack.inCoverage(dest.latitude,dest.longitude)
  // Android-validated internet only; captive portals / "no internet" go straight to the device router.
  val network=currentNetworkStatus(getApplication())
  router.cancel();routeJob?.cancel()
  routeJob=viewModelScope.launch {
   val configured=withContext(Dispatchers.IO){googleRoutes!=null}
   val decision=RoutingPolicy.decide(network,configured,covered)
   if(decision is RoutingDecision.Unavailable){_ui.update{it.copy(busy=false,message=decision.reason)};return@launch}
   _ui.update {it.copy(busy=true,message=when{
    decision is RoutingDecision.Online -> if(reroute)"Recalculating with Google walking directions..." else "Requesting a Google walking route online..."
    reroute -> "Recalculating locally..."
    else -> "Finding a walking route offline..."
   })}
   try {
    val planned=planner.plan(RouteCoordinate(origin.latitude,origin.longitude),RouteCoordinate(dest.latitude,dest.longitude),decision)
    val route=planned.route
    ensureActive();withContext(Dispatchers.IO){savedRoute.save(SavedRoute(dest,route,start))};ensureActive();tracker=RouteProgressTracker(route.points.map{RoutePoint(it.latitude,it.longitude)})
    _ui.update {it.copy(route=route,busy=false,progress=null,message=resultMessage(planned),navigating=reroute)}
   } catch(e:CancellationException){throw e} catch(e:Exception){ensureActive();_ui.update {it.copy(busy=false,message=e.message?:"Could not calculate a route")}}
  }
 }
 private fun resultMessage(planned:PlannedRoute):String {
  val route=planned.route
  return when {
   route.source==RouteSource.GOOGLE_ROUTES -> "Google walking route, fetched online in ${route.calculationMillis} ms. "+route.warnings.joinToString(" ")
   planned.onlineFailure!=null -> "Google route failed: ${planned.onlineFailure.userText}. Offline route computed on this phone in ${route.calculationMillis} ms."
   planned.offlineReason=="No internet" -> "Computed on this phone in ${route.calculationMillis} ms / no internet"
   else -> "Computed on this phone in ${route.calculationMillis} ms / ${planned.offlineReason?.replaceFirstChar{it.lowercase()}?:"offline"}"
  }
 }
 private fun followingMessage()=if(_ui.value.route?.source==RouteSource.GOOGLE_ROUTES)"Following GPS / Google walking route" else "Following GPS / offline walking"
 fun navigate() {
  if(_ui.value.manualOrigin!=null){_ui.update{it.copy(message="Switch to GPS start and recalculate before following this route.")};return}
  if(_ui.value.route==null)return
  val state=gps.state.value;val fix=state.fix
  if(state.status!=GpsStatus.FIX || fix==null || fix.cached || fix.ageMillis()>15000 || (fix.accuracyMeters?:999f)>30f) {
   _ui.update{it.copy(message="Waiting for a fresh GPS fix within 30 m accuracy before guidance starts.")};return
  }
  pendingNavigation=true
  val result=OfflineNavigationLocationService.start(getApplication())
  if(result.isFailure){pendingNavigation=false;_ui.update{it.copy(message=result.exceptionOrNull()?.message?:"Could not start guidance")}}
  else if(OfflineNavigationLocationService.running.value){pendingNavigation=false;_ui.update{it.copy(navigating=true,message=followingMessage())}}
  else _ui.update{it.copy(message="Starting guidance...")}
 }
 private fun stopLocationGuidance(){pendingNavigation=false;OfflineNavigationLocationService.stop(getApplication())}
 fun stop(){stopLocationGuidance();_ui.update{it.copy(navigating=false,progress=null,message="Guidance stopped")}}
 fun search(query:String):List<Place> {val q=OfflinePack.normalized(query);if(q.length<2)return emptyList();val origin=gps.state.value.fix?.point;return _ui.value.places.filter{OfflinePack.normalized(it.name+" "+it.aliases).contains(q)}.sortedBy {if(origin==null)0.0 else RouteGeometry.distance(origin,RoutePoint(it.latitude,it.longitude))}.take(12)}
 override fun onCleared(){router.cancel();stopLocationGuidance();super.onCleared()}
}
