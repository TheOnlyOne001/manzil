package app.manzil.offline

import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import android.widget.FrameLayout
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import app.manzil.offline.location.GpsFix
import app.manzil.offline.routing.OfflineRoute

/** Google basemap, with Manzil-owned routing and overlays. No Google data is saved to the local pack. */
class GoogleNativeMapView(context:Context,private val loaded:()->Unit):FrameLayout(context),MapSurface {
 private val view=MapView(context,GoogleMapOptions().mapColorScheme(MapColorScheme.LIGHT))
 private var map:GoogleMap?=null
 private var bottomInset=(245*resources.displayMetrics.density).toInt()
 fun contentBottomInset(pixels:Int){if(bottomInset==pixels)return;bottomInset=pixels;map?.setPadding(0,(120*resources.displayMetrics.density).toInt(),0,bottomInset)}
 private var pendingCamera:MapCamera?=null
 private var route:OfflineRoute?=null
 private var fix:GpsFix?=null
 private var destination:Place?=null
 private var line:Polyline?=null
 private var casing:Polyline?=null
 private var position:Marker?=null
 private var accuracy:Circle?=null
 private var destinationDot:Circle?=null
 private var night=false
 private var follow=true
 private var lastFix=0L
 override var onPick:(Double,Double)->Unit={_,_->}
 override var onTap:(PointF)->Boolean={false}
 override var onCameraChanged:()->Unit={}
 init {
  addView(view,LayoutParams(-1,-1));view.onCreate(null)
  view.getMapAsync { m->map=m
   m.uiSettings.isMapToolbarEnabled=false;m.uiSettings.isZoomControlsEnabled=false
   m.uiSettings.isMyLocationButtonEnabled=false;m.uiSettings.isCompassEnabled=false
   val d=resources.displayMetrics.density
   m.setPadding(0,(120*d).toInt(),0,bottomInset)
   m.setMapStyle(MapStyleOptions("""[{"featureType":"poi.business","stylers":[{"visibility":"off"}]},{"featureType":"poi","elementType":"labels.icon","stylers":[{"visibility":"off"}]},{"featureType":"water","elementType":"geometry","stylers":[{"color":"#c4dfe7"}]}]"""))
   nightMode(night)
   m.setOnMapLongClickListener{onPick(it.latitude,it.longitude)}
   m.setOnMapClickListener{val p=m.projection.toScreenLocation(it);onTap(PointF(p.x.toFloat(),p.y.toFloat()))}
   m.setOnCameraMoveListener{onCameraChanged()}
   m.setOnCameraMoveStartedListener{if(it==GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE)follow=false}
   move(pendingCamera?:MapCamera(12.823,80.045,14.0))
   drawRoute();drawPosition()
   m.setOnMapLoadedCallback{loaded();onCameraChanged()}
  }
 }
 override fun nightMode(enabled:Boolean){night=enabled;map?.setMapStyle(MapStyleOptions(if(enabled) """[{"elementType":"geometry","stylers":[{"color":"#142735"}]},{"elementType":"labels.text.fill","stylers":[{"color":"#91a8b7"}]},{"elementType":"labels.text.stroke","stylers":[{"color":"#142735"}]},{"featureType":"road","elementType":"geometry","stylers":[{"color":"#304654"}]},{"featureType":"water","elementType":"geometry","stylers":[{"color":"#0b1b29"}]},{"featureType":"poi","elementType":"labels","stylers":[{"visibility":"off"}]},{"featureType":"transit","elementType":"labels.icon","stylers":[{"visibility":"off"}]}]""" else """[{"featureType":"poi.business","stylers":[{"visibility":"off"}]},{"featureType":"poi","elementType":"labels.icon","stylers":[{"visibility":"off"}]},{"featureType":"water","elementType":"geometry","stylers":[{"color":"#c4dfe7"}]}]"""))}
 override fun setPlaces(places:List<Place>)=Unit
 // Shared camera uses MapLibre's 512px tile zoom; Google uses a 256px tile zoom.
 override fun camera():MapCamera?=map?.cameraPosition?.let{MapCamera(it.target.latitude,it.target.longitude,it.zoom.toDouble()-1.0,it.bearing.toDouble(),it.tilt.toDouble())}
 override fun move(camera:MapCamera){pendingCamera=camera;map?.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(LatLng(camera.latitude,camera.longitude)).zoom((camera.zoom+1.0).toFloat()).bearing(camera.bearing.toFloat()).tilt(camera.tilt.toFloat()).build()))}
 override fun project(latitude:Double,longitude:Double):PointF?=map?.projection?.toScreenLocation(LatLng(latitude,longitude))?.let{PointF(it.x.toFloat(),it.y.toFloat())}
 override fun update(route:OfflineRoute?,fix:GpsFix?,destination:Place?,navigating:Boolean){
  val changed=this.route!==route;val destChanged=this.destination!=destination;val fixChanged=this.fix!=fix
  this.route=route;this.fix=fix;this.destination=destination
  if(changed) {drawRoute();if(route!=null)fitRoute()}
  if(destChanged && route==null && destination!=null)move(MapCamera(destination.latitude,destination.longitude,16.0))
  if(destChanged || fixChanged)drawPosition()
  if(navigating && follow && fix!=null && !fix.cached && fix.ageMillis()<15000 && (fix.accuracyMeters?:999f)<=30f && lastFix!=fix.elapsedRealtimeMillis){lastFix=fix.elapsedRealtimeMillis;center()}
 }
 private fun drawRoute(){val m=map?:return;line?.remove();casing?.remove();line=null;casing=null
  route?.takeIf{it.points.size>1}?.let { r->val points=r.points.map{LatLng(it.latitude,it.longitude)};val d=resources.displayMetrics.density
   casing=m.addPolyline(PolylineOptions().addAll(points).color(Color.WHITE).width(8*d).startCap(RoundCap()).endCap(RoundCap()).jointType(JointType.ROUND).zIndex(5f))
   line=m.addPolyline(PolylineOptions().addAll(points).color(Color.rgb(36,87,214)).width(5*d).startCap(RoundCap()).endCap(RoundCap()).jointType(JointType.ROUND).zIndex(6f))
  }
 }
 private fun drawPosition(){val m=map?:return
  position?.remove();accuracy?.remove();destinationDot?.remove();position=null;destinationDot=null
  fix?.let{val fresh=!it.cached&&it.ageMillis()<15000&&(it.accuracyMeters?:999f)<=30f
   val density=resources.displayMetrics.density;val size=(32*density).toInt()
   val bitmap=android.graphics.Bitmap.createBitmap(size,size,android.graphics.Bitmap.Config.ARGB_8888)
   val canvas=android.graphics.Canvas(bitmap);val p=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);val c=size/2f
   p.color=Color.WHITE;canvas.drawCircle(c,c,12*density,p)
   p.color=if(fresh)Color.rgb(36,87,214) else Color.rgb(204,126,30);canvas.drawCircle(c,c,9*density,p)
   p.color=Color.WHITE;canvas.drawCircle(c,c,3*density,p)
   val point=LatLng(it.point.latitude,it.point.longitude)
   position=m.addMarker(MarkerOptions().position(point).anchor(.5f,.5f).icon(BitmapDescriptorFactory.fromBitmap(bitmap)).title(if(fresh)"Your location" else "Approximate location").zIndex(20f))
   accuracy=m.addCircle(CircleOptions().center(point).radius((it.accuracyMeters?:50f).toDouble().coerceIn(5.0,1000.0)).fillColor(if(fresh)0x222457D6 else 0x22CC7E1E).strokeColor(if(fresh)0x662457D6 else 0x66CC7E1E).strokeWidth(density).zIndex(2f))}

  destination?.let{destinationDot=m.addCircle(CircleOptions().center(LatLng(it.latitude,it.longitude)).radius(5.0).fillColor(Color.rgb(20,45,78)).strokeColor(Color.WHITE).strokeWidth(2*resources.displayMetrics.density).zIndex(7f))}
 }
 private fun fitRoute(){val r=route?:return;if(r.points.size<2)return;val b=LatLngBounds.builder();r.points.forEach{b.include(LatLng(it.latitude,it.longitude))};post{map?.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(),(32*resources.displayMetrics.density).toInt()))}}
 override fun center(){follow=true;fix?.let{map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.point.latitude,it.point.longitude),16f))}}
 override fun start(){view.onStart()}
 override fun resume(){view.onResume()}
 override fun pause(){view.onPause()}
 override fun stop(){view.onStop()}
 override fun destroy(){view.onDestroy()}
}
