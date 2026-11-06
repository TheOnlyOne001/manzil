package app.manzil.offline

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import app.manzil.BuildConfig
import app.manzil.system.NetworkStatus
import app.manzil.offline.location.GpsFix
import app.manzil.offline.routing.OfflineRoute

/** Keep the local map visible until Google actually loads. Connectivity loss restores local data. */
class AdaptiveMapView(context:Context,private val pack:OfflinePack):FrameLayout(context) {
 private var active:MapSurface=OfflineMapView(context,pack)
 private var pending:GoogleNativeMapView?=null
 private var bottomInset=(245*resources.displayMetrics.density).toInt()
 fun contentBottomInset(pixels:Int){bottomInset=pixels;(active as? GoogleNativeMapView)?.contentBottomInset(pixels);pending?.contentBottomInset(pixels)}
 private val hazards=HazardCanvas(context)
 private val routeMarkers=RouteMarkersCanvas(context)
 private val nightCanvas=NightWalkCanvas(context)
 private var night=false
 private var started=false
 private var resumed=false
 private var dead=false
 private var failed=false
 private var network=NetworkStatus.OFFLINE
 private var places:List<Place> = emptyList()
 private var route:OfflineRoute?=null
 private var fix:GpsFix?=null
 private var destination:Place?=null
 private var navigating=false
 var onPick:(Double,Double)->Unit={_,_->}
 var onProvider:(MapProvider)->Unit={}
 private val timeout=Runnable{pending?.let{dispose(it);pending=null;failed=true;onProvider(MapProvider.LOCAL)}}
 init {addView(active as View,LayoutParams(-1,-1));addView(hazards,LayoutParams(-1,-1));addView(routeMarkers,LayoutParams(-1,-1));addView(nightCanvas,LayoutParams(-1,-1));bind(active)}
 private fun bind(map:MapSurface){map.onPick={a,b->onPick(a,b)};map.onTap={if(night)nightCanvas.tap(it) else hazards.tap(it)};map.onCameraChanged={hazards.invalidate();routeMarkers.invalidate();nightCanvas.invalidate()};hazards.surface=map;nightCanvas.surface=map;routeMarkers.surface=map;routeMarkers.invalidate()}
 fun setPlaces(value:List<Place>){places=value;active.setPlaces(value)}
 fun overlays(features:List<HazardFeature>,layer:HazardLayer){hazards.show(features,if(layer==HazardLayer.NIGHT)HazardLayer.NONE else layer);nightCanvas.show(features,layer==HazardLayer.NIGHT);if(night!=(layer==HazardLayer.NIGHT)){night=layer==HazardLayer.NIGHT;active.nightMode(night);pending?.nightMode(night)}}
 fun update(route:OfflineRoute?,fix:GpsFix?,destination:Place?,navigating:Boolean){this.route=route;nightCanvas.context(route,fix,destination);routeMarkers.route=route;routeMarkers.invalidate();this.fix=fix;this.destination=destination;this.navigating=navigating;active.update(route,fix,destination,navigating);pending?.update(route,fix,destination,navigating);hazards.invalidate()}
 fun network(status:NetworkStatus){
  if(dead)return
  if(status!=network){failed=false;network=status}
  val available=GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)==ConnectionResult.SUCCESS
  val target=MapProviderPolicy.choose(status,BuildConfig.GOOGLE_MAPS_CONFIGURED,available,failed)
  if(target==MapProvider.LOCAL){removeCallbacks(timeout);pending?.let{dispose(it)};pending=null
   if(active is GoogleNativeMapView){val camera=active.camera();dispose(active);active=OfflineMapView(context,pack);addView(active as View,0,LayoutParams(-1,-1));bind(active);hydrate(active,camera)}
   onProvider(MapProvider.LOCAL);return
  }
  if(active is GoogleNativeMapView || pending!=null)return
  val camera=active.camera()
  try {
   lateinit var candidate:GoogleNativeMapView
   candidate=GoogleNativeMapView(context){post{val next=pending?:return@post;if(next!==candidate || network!=NetworkStatus.ONLINE || dead)return@post
    removeCallbacks(timeout);val currentCamera=active.camera();dispose(active);active=next;pending=null;bind(next);currentCamera?.let{next.move(it)};onProvider(MapProvider.GOOGLE);hazards.bringToFront();routeMarkers.bringToFront();nightCanvas.bringToFront()
   }}
   pending=candidate;candidate.contentBottomInset(bottomInset);addView(candidate,0,LayoutParams(-1,-1));hydrate(candidate,camera)
   postDelayed(timeout,15000)
  }catch(_:RuntimeException){pending?.let{dispose(it)};pending=null;failed=true;onProvider(MapProvider.LOCAL)}
 }
 private fun hydrate(map:MapSurface,camera:MapCamera?){map.nightMode(night);map.setPlaces(places);map.update(route,fix,destination,navigating);camera?.let{map.move(it)};if(started)map.start();if(resumed)map.resume()}
 private fun dispose(map:MapSurface){if(resumed)map.pause();if(started)map.stop();removeView(map as View);map.destroy()}
 fun center(){active.center()}
 fun exploreLighting(features:List<HazardFeature>){
  val camera=active.camera();val lit=features.filter{it.layer==HazardLayer.NIGHT && it.kind=="night_lit"}
  val target=lit.minByOrNull{val lat=camera?.latitude?:13.055;val lon=camera?.longitude?:80.235;(it.point.latitude-lat)*(it.point.latitude-lat)+(it.point.longitude-lon)*(it.point.longitude-lon)}
  target?.let{active.move(MapCamera(it.point.latitude,it.point.longitude,15.0))};nightCanvas.invalidate()
 }
 fun showCoverage(){active.move(MapCamera(13.055,80.235,12.0));hazards.invalidate()}
 fun start(){started=true;active.start();pending?.start()}
 fun resume(){resumed=true;active.resume();pending?.resume()}
 fun pause(){resumed=false;active.pause();pending?.pause()}
 fun stop(){started=false;active.stop();pending?.stop()}
 fun destroy(){dead=true;removeCallbacks(timeout);pending?.let{dispose(it)};pending=null;dispose(active);hazards.destroy();nightCanvas.destroy()}
}
