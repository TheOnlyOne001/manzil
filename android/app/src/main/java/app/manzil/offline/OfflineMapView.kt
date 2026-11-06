package app.manzil.offline

import android.content.Context
import android.graphics.*
import android.widget.FrameLayout
import android.view.View
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.*
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.style.layers.*
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.expressions.Expression.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.*
import app.manzil.offline.routing.OfflineRoute
import app.manzil.offline.location.GpsFix
import java.io.File
import kotlin.math.floor
import kotlinx.coroutines.*

class OfflineMapView(context:Context,private val pack:OfflinePack):FrameLayout(context),MapSurface {
 val mapView:MapView
 private var map:MapLibreMap?=null
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 private var tileJob:Job?=null
 private var loadedKey=""
 private var route:OfflineRoute?=null
 private var fix:GpsFix?=null
 private var destination:Place?=null
 private var labels:List<Place> = emptyList()
 private var night=false
 private var firstFix=true
 private var followLocation=true
 private var lastFollowFix=0L
 override var onPick:(Double,Double)->Unit={_,_->}
 override var onTap:(PointF)->Boolean={false}
 override var onCameraChanged:()->Unit={}
 private var pendingCamera:MapCamera?=null
 private val textPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)}
 private val labelView=object:View(context) {
  override fun onDraw(canvas:Canvas) {
   val m=map?:return
   if(m.cameraPosition.zoom<13)return
   val occupied=mutableListOf<RectF>()
   val d=resources.displayMetrics.density
   val visible=m.projection.visibleRegion.latLngBounds
   labels.asSequence().filter{it.latitude in visible.latitudeSouth..visible.latitudeNorth && it.longitude in visible.longitudeWest..visible.longitudeEast}.filter{it.kind!="road" || m.cameraPosition.zoom>=15}.take(160).forEach { place ->
    val p=m.projection.toScreenLocation(LatLng(place.latitude,place.longitude))
    if(p.x<20*d||p.x>width-20*d||p.y<130*d||p.y>height-210*d)return@forEach
    val name=place.name.take(34);textPaint.textSize=11*d
    val w=textPaint.measureText(name);val rect=RectF(p.x-w/2-4*d,p.y-13*d,p.x+w/2+4*d,p.y+5*d)
    if(occupied.any{RectF.intersects(it,rect)})return@forEach
    occupied.add(rect);textPaint.style=Paint.Style.STROKE;textPaint.strokeWidth=3*d;textPaint.color=if(night)Color.rgb(20,39,53) else Color.WHITE
    canvas.drawText(name,p.x-w/2,p.y,textPaint);textPaint.style=Paint.Style.FILL;textPaint.color=if(night)Color.rgb(145,168,183) else Color.rgb(61,79,93);canvas.drawText(name,p.x-w/2,p.y,textPaint)
   }
  }
 }
 init {
  MapLibre.getInstance(context)
  mapView=MapView(context,MapLibreMapOptions.createFromAttributes(context).textureMode(true))
  addView(mapView,LayoutParams(-1,-1));addView(labelView,LayoutParams(-1,-1))
  mapView.onCreate(null)
  mapView.getMapAsync { m ->
   map=m;m.uiSettings.isLogoEnabled=false;m.uiSettings.isAttributionEnabled=false
   m.cameraPosition=org.maplibre.android.camera.CameraPosition.Builder().target(LatLng(12.823,80.045)).zoom(14.0).build()
   m.setStyle(Style.Builder().fromJson("""{"version":8,"sources":{},"layers":[{"id":"background","type":"background","paint":{"background-color":"#EDF1ED"}}]}""")) { style ->
    val contextFile=File(pack.map,"context-srm.geojson")
    if(contextFile.isFile) {
     style.addSource(GeoJsonSource("context",FeatureCollection.fromJson(contextFile.readText())))
     for((kind,color) in listOf("water" to "#B4DDE5","park" to "#D3E6CE","building" to "#E0E1DB")) {
      style.addLayer(FillLayer("context-$kind","context").withFilter(eq(get("class"),literal(kind))).withProperties(fillColor(color)))
     }
    }
    style.addSource(GeoJsonSource("roads",FeatureCollection.fromFeatures(emptyArray<Feature>())))
    style.addLayer(LineLayer("road-case","roads").withProperties(lineColor("#CCD5D7"),lineWidth(interpolate(linear(),zoom(),stop(10,1),stop(14,3),stop(17,6))),lineCap(Property.LINE_CAP_ROUND)))
    style.addLayer(LineLayer("road-fill","roads").withProperties(lineColor("#FFFFFF"),lineWidth(interpolate(linear(),zoom(),stop(10,.5),stop(14,2),stop(17,4))),lineCap(Property.LINE_CAP_ROUND)))
    style.addLayer(LineLayer("paths","roads").withFilter(eq(get("class"),literal("path"))).withProperties(lineColor("#98B7AA"),lineWidth(2f),lineDasharray(arrayOf(2f,2f))))
    style.addLayer(LineLayer("major","roads").withFilter(eq(get("class"),literal("major"))).withProperties(lineColor("#F3D6A4"),lineWidth(interpolate(linear(),zoom(),stop(10,1),stop(14,2.5),stop(17,5)))))
    style.addSource(GeoJsonSource("route",FeatureCollection.fromFeatures(emptyArray<Feature>())))
    style.addLayer(LineLayer("route-case","route").withProperties(lineColor("#FFFFFF"),lineWidth(10f),lineCap(Property.LINE_CAP_ROUND),lineJoin(Property.LINE_JOIN_ROUND)))
    style.addLayer(LineLayer("route-line","route").withProperties(lineColor("#2457D6"),lineWidth(6f),lineCap(Property.LINE_CAP_ROUND),lineJoin(Property.LINE_JOIN_ROUND)))
    style.addSource(GeoJsonSource("destination",FeatureCollection.fromFeatures(emptyArray<Feature>())))
    style.addLayer(CircleLayer("destination-dot","destination").withProperties(circleColor("#142D4E"),circleRadius(7f),circleStrokeColor("#FFFFFF"),circleStrokeWidth(3f)))
    style.addSource(GeoJsonSource("position",FeatureCollection.fromFeatures(emptyArray<Feature>())))
    style.addLayer(CircleLayer("position-halo","position").withProperties(circleColor("#2457D6"),circleOpacity(.12f),circleRadius(22f)))
    style.addLayer(CircleLayer("position-dot","position").withProperties(circleColor("#2457D6"),circleRadius(7f),circleStrokeColor("#FFFFFF"),circleStrokeWidth(3f)))
    nightMode(night);refreshTiles();drawState();if(pendingCamera!=null)move(pendingCamera!!) else fitRoute()
    if(route==null && pendingCamera==null)destination?.let{m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude,it.longitude),16.0))}
   }
   m.addOnCameraIdleListener{refreshTiles();labelView.invalidate()}
   m.addOnCameraMoveListener{labelView.invalidate();onCameraChanged()}
   m.addOnCameraMoveStartedListener { reason -> if(reason==MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE)followLocation=false }
   m.addOnMapClickListener { point -> onTap(m.projection.toScreenLocation(point)) }
   m.addOnMapLongClickListener { point -> onPick(point.latitude,point.longitude);true }
  }
 }
 override fun setPlaces(places:List<Place>){if(labels!==places){labels=places;loadedKey="";labelView.invalidate()};refreshTiles()}
 private fun refreshTiles() {
  val m=map?:return;val center=m.cameraPosition.target?:return
  if(m.style?.getSourceAs<GeoJsonSource>("roads")==null)return
  val contextFile=File(pack.map,"context-srm.geojson")
  val style=m.style!!
  if(style.getSource("context")==null && contextFile.isFile) {
   style.addSource(GeoJsonSource("context",FeatureCollection.fromJson(contextFile.readText())))
   for((kind,color) in listOf("water" to "#B4DDE5","park" to "#D3E6CE","building" to "#E0E1DB")) {
    style.addLayerBelow(FillLayer("context-$kind","context").withFilter(eq(get("class"),literal(kind))).withProperties(fillColor(color)),"road-case")
   }
  }
  val zoom=m.cameraPosition.zoom
  val keys=if(zoom<12.5)listOf("overview.geojson") else {
   val bounds=m.projection.visibleRegion.latLngBounds
   val west=floor((bounds.longitudeWest-79.95)/.025).toInt().coerceIn(0,15);val east=floor((bounds.longitudeEast-79.95)/.025).toInt().coerceIn(0,15)
   val south=floor((bounds.latitudeSouth-12.65)/.025).toInt().coerceIn(0,25);val north=floor((bounds.latitudeNorth-12.65)/.025).toInt().coerceIn(0,25)
   (west..east).flatMap{x->(south..north).map{y->"tiles/${x}_${y}.geojson"}}
  }
  val key=keys.joinToString();if(key==loadedKey)return;loadedKey=key
  tileJob?.cancel();tileJob=scope.launch {
   val data=withContext(Dispatchers.IO){keys.flatMap {path->val f=File(pack.map,path);if(f.isFile)runCatching{FeatureCollection.fromJson(f.readText()).features().orEmpty()}.onFailure{android.util.Log.e("ManzilMap","Cannot read $f",it)}.getOrDefault(emptyList()) else emptyList()}}
   android.util.Log.i("ManzilMap","root=${pack.map} keys=$key features=${data.size}")
   ensureActive();m.style?.getSourceAs<GeoJsonSource>("roads")?.setGeoJson(FeatureCollection.fromFeatures(data))
   if(data.isEmpty())loadedKey=""
   labelView.invalidate()
  }
 }
 override fun update(newRoute:OfflineRoute?,newFix:GpsFix?,dest:Place?,navigating:Boolean) {
  val changed=route!==newRoute;val destinationChanged=destination!=dest;route=newRoute;fix=newFix;destination=dest;drawState()
  if(destinationChanged && dest!=null && newRoute==null)map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(dest.latitude,dest.longitude),16.0))
  if(changed && newRoute!=null && newRoute.points.size>1) {
   fitRoute()
  } else if(firstFix && pendingCamera==null && newRoute==null && dest==null && newFix!=null && !newFix.cached && newFix.ageMillis()<15000 && (newFix.accuracyMeters?:999f)<=30f) {firstFix=false;center()}
  if(navigating && followLocation && newFix!=null && !newFix.cached && newFix.ageMillis()<15000 && (newFix.accuracyMeters?:999f)<=30f && newFix.elapsedRealtimeMillis!=lastFollowFix) {
   lastFollowFix=newFix.elapsedRealtimeMillis
   map?.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(newFix.point.latitude,newFix.point.longitude),maxOf(map?.cameraPosition?.zoom?:16.0,16.0)))
  }
 }
 private fun fitRoute(){val current=route?:return;if(current.points.size<2)return
  val bounds=LatLngBounds.Builder();current.points.forEach{bounds.include(LatLng(it.latitude,it.longitude))}
  val d=resources.displayMetrics.density
  map?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(),(40*d).toInt(),(160*d).toInt(),(40*d).toInt(),(280*d).toInt()))
 }
 override fun nightMode(enabled:Boolean){night=enabled;val style=map?.style?:return
  style.getLayerAs<org.maplibre.android.style.layers.BackgroundLayer>("background")?.setProperties(backgroundColor(if(enabled)"#142735" else "#EDF1ED"))
  for((id,light,dark) in listOf(Triple("road-case","#CCD5D7","#263D4B"),Triple("road-fill","#FFFFFF","#304654"),Triple("major","#F3D6A4","#3C515D"),Triple("paths","#98B7AA","#3A5360")))style.getLayerAs<LineLayer>(id)?.setProperties(lineColor(if(enabled)dark else light))
  for((id,light,dark) in listOf(Triple("water","#B4DDE5","#0B1B29"),Triple("park","#D3E6CE","#183B3E"),Triple("building","#E0E1DB","#233845")))style.getLayerAs<FillLayer>("context-$id")?.setProperties(fillColor(if(enabled)dark else light))
  labelView.invalidate()
 }
 override fun center(){followLocation=true;fix?.let{map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.point.latitude,it.point.longitude),16.0))}}
 private fun drawState(){val style=map?.style?:return
  val reliable=fix?.let{!it.cached && it.ageMillis()<15000 && (it.accuracyMeters?:999f)<=30f}==true
  style.getLayerAs<CircleLayer>("position-dot")?.setProperties(circleColor(if(reliable)"#2457D6" else "#87939E"))
  style.getLayerAs<CircleLayer>("position-halo")?.setProperties(circleColor(if(reliable)"#2457D6" else "#87939E"))
  style.getSourceAs<GeoJsonSource>("route")?.setGeoJson(route?.let{Feature.fromGeometry(LineString.fromLngLats(it.points.map{p->org.maplibre.geojson.Point.fromLngLat(p.longitude,p.latitude)}))}?:Feature.fromGeometry(GeometryCollection.fromGeometries(emptyList())))
  style.getSourceAs<GeoJsonSource>("position")?.setGeoJson(fix?.let{Feature.fromGeometry(org.maplibre.geojson.Point.fromLngLat(it.point.longitude,it.point.latitude))}?:Feature.fromGeometry(GeometryCollection.fromGeometries(emptyList())))
  style.getSourceAs<GeoJsonSource>("destination")?.setGeoJson(destination?.let{Feature.fromGeometry(org.maplibre.geojson.Point.fromLngLat(it.longitude,it.latitude))}?:Feature.fromGeometry(GeometryCollection.fromGeometries(emptyList())))
 }
 override fun camera():MapCamera?=map?.cameraPosition?.let{c->c.target?.let{MapCamera(it.latitude,it.longitude,c.zoom,c.bearing,c.tilt)}}
 override fun move(camera:MapCamera){pendingCamera=camera;map?.moveCamera(CameraUpdateFactory.newCameraPosition(org.maplibre.android.camera.CameraPosition.Builder().target(LatLng(camera.latitude,camera.longitude)).zoom(camera.zoom).bearing(camera.bearing).tilt(camera.tilt).build()))}
 override fun project(latitude:Double,longitude:Double):PointF?=map?.projection?.toScreenLocation(LatLng(latitude,longitude))
 override fun start(){mapView.onStart()}
 override fun resume(){mapView.onResume()}
 override fun pause(){mapView.onPause()}
 override fun stop(){mapView.onStop()}
 override fun destroy(){scope.cancel();mapView.onDestroy()}
}
