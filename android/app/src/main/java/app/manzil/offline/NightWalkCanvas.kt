package app.manzil.offline

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.provider.Settings
import android.view.View
import kotlin.math.*
import kotlinx.coroutines.*

/** Mapped lighting evidence, not a safety score. All strokes follow geographic road geometry. */
class NightWalkCanvas(context:Context):View(context) {
 var surface:MapSurface?=null
 private var features:List<HazardFeature> = emptyList()
 private var original:List<HazardFeature>?=null
 private var route:app.manzil.offline.routing.OfflineRoute?=null
 private var anchor:HazardPoint?=null
 private var relevantIds:Set<String> = emptySet()
 private val selectionScope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private var selectionJob:Job?=null
 fun context(route:app.manzil.offline.routing.OfflineRoute?,fix:app.manzil.offline.location.GpsFix?,destination:Place?){
  val next=fix?.point?.let{HazardPoint(it.latitude,it.longitude)}?:destination?.let{HazardPoint(it.latitude,it.longitude)}
  if(this.route===route && anchor==next)return
  this.route=route;anchor=next;selectSupport();invalidate()
 }
 private fun selectSupport(){
  // Geographic selection is cached, never repeated while the camera animates.
  selectionJob?.cancel()
  val points=route?.points.orEmpty()
  val candidates=features
  val near=anchor
  selectionJob=selectionScope.launch {
  val ids=withContext(Dispatchers.Default) {
  val segments=points.zipWithNext()
  fun meters(p:HazardPoint,a:Double,b:Double)=hypot((p.latitude-a)*111320,(p.longitude-b)*111320*cos(Math.toRadians(p.latitude)))
  fun corridor(p:HazardPoint):Double {
   if(points.size<2)return Double.POSITIVE_INFINITY
   val scale=111320*cos(Math.toRadians(p.latitude))
   return segments.minOf{(a,b)->
    val ax=(a.longitude-p.longitude)*scale;val ay=(a.latitude-p.latitude)*111320
    val dx=(b.longitude-a.longitude)*scale;val dy=(b.latitude-a.latitude)*111320
    val t=if(dx*dx+dy*dy==0.0)0.0 else (-(ax*dx+ay*dy)/(dx*dx+dy*dy)).coerceIn(0.0,1.0)
    hypot(ax+t*dx,ay+t*dy)
   }
  }
  candidates.asSequence().filter{it.kind=="night_support"}.map{f->ensureActive();f to min(corridor(f.point),near?.let{meters(f.point,it.latitude,it.longitude)}?:Double.POSITIVE_INFINITY)}.filter{it.second<=400.0}.sortedBy{it.second}.map{it.first.id}.toSet()
  }
  relevantIds=ids;invalidate()
  }
 }
 private var enabled=false
 private var selected:HazardFeature?=null
 private var fade=1f
 private var animator:ValueAnimator?=null
 private val d=resources.displayMetrics.density
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
 private val hits=mutableListOf<Pair<HazardFeature,List<PointF>>>()
 fun show(data:List<HazardFeature>,active:Boolean){
  if(original===data && enabled==active)return
  val entering=active&&!enabled;enabled=active;original=data
  features=data.filter{it.layer==HazardLayer.NIGHT}.sortedBy{if(it.kind=="night_unknown")0 else if(it.kind=="night_support")2 else 1}
  selectSupport()
  selected=null;animator?.cancel();fade=1f;visibility=if(active)VISIBLE else GONE
  contentDescription="Night Walk. Mapped lighting, lighting unknown and support places. Not live verified."
  if(entering && Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f){animator=ValueAnimator.ofFloat(0f,1f).apply{duration=1100;addUpdateListener{fade=it.animatedValue as Float;invalidate()};start()}}
  invalidate()
 }
 private fun distance(p:PointF,a:PointF,b:PointF):Float{val dx=b.x-a.x;val dy=b.y-a.y;val t=if(dx*dx+dy*dy==0f)0f else (((p.x-a.x)*dx+(p.y-a.y)*dy)/(dx*dx+dy*dy)).coerceIn(0f,1f);return hypot(p.x-a.x-t*dx,p.y-a.y-t*dy)}
 fun tap(p:PointF):Boolean {
  if(!enabled)return false
  val hit=hits.map{(f,pts)->f to if(pts.size==1)hypot(p.x-pts[0].x,p.y-pts[0].y) else pts.zipWithNext().minOf{distance(p,it.first,it.second)}}.minByOrNull{it.second}?.takeIf{it.second<22*d}
  selected=hit?.first;contentDescription=selected?.let{"${it.title}. ${it.detail}"}?:"Night Walk. Tap a road for lighting evidence."
  invalidate();return hit!=null
 }
 override fun onDraw(canvas:Canvas){super.onDraw(canvas);hits.clear();if(!enabled)return;val map=surface?:return;val camera=map.camera()?:return
  val span=360.0/2.0.pow(camera.zoom)*max(width,height)/256.0
  canvas.save()
  route?.points?.firstOrNull()?.let{map.project(it.latitude,it.longitude)}?.let{p->canvas.clipOutRect(p.x-21*d,p.y-21*d,p.x+21*d,p.y+40*d)}
  route?.points?.lastOrNull()?.let{map.project(it.latitude,it.longitude)}?.let{p->canvas.clipOutRect(p.x-19*d,p.y-46*d,p.x+19*d,p.y+3*d)}
  val litPaths=Path()
  val bubbles=mutableListOf<PointF>()
  val categoryCounts=mutableMapOf<String,Int>()
  for(f in features){
   if(f.kind=="night_support")continue
   if(abs(f.point.latitude-camera.latitude)>span || abs(f.point.longitude-camera.longitude)>span)continue
   val center=map.project(f.point.latitude,f.point.longitude)?:continue
   val pts=if(f.road.size>1)f.road.mapNotNull{map.project(it.latitude,it.longitude)} else listOf(center)
   if(pts.none{it.x in -100*d..width+100*d && it.y in 0f..height.toFloat()})continue
   val focused=selected?.id==f.id
   if(pts.size<2)continue
   val path=Path().apply{moveTo(pts[0].x,pts[0].y);pts.drop(1).forEach{lineTo(it.x,it.y)}}
   val lit=f.kind=="night_lit";val unlit=f.kind=="night_unlit"
   paint.style=Paint.Style.STROKE;paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND;paint.pathEffect=null
   paint.color=if(lit)Color.rgb(255,211,114) else if(unlit)Color.rgb(201,141,145) else Color.rgb(111,144,163)
   if(lit){litPaths.addPath(path)}else{paint.strokeWidth=(if(focused)3 else if(unlit)2 else 1)*d;paint.alpha=((if(focused)220 else if(unlit)170 else 90)*fade).toInt();paint.pathEffect=DashPathEffect(floatArrayOf(4*d,5*d),0f);canvas.drawPath(path,paint);paint.pathEffect=null}
   hits.add(f to pts)
  }
  // One network stroke prevents overlapping segments accumulating into bright blobs.
  val scale=((camera.zoom-9.0)/5.0).coerceIn(0.0,1.0).toFloat()
  paint.style=Paint.Style.STROKE;paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND;paint.pathEffect=null
  paint.color=Color.rgb(255,211,114)
  for((width,alpha) in listOf((5f+7f*scale) to 18,(2.8f+4f*scale) to 38,(1.3f+1.3f*scale) to 200)){
   paint.strokeWidth=width*d;paint.alpha=(alpha*fade).toInt();canvas.drawPath(litPaths,paint)
  }
  paint.color=Color.rgb(255,235,182);paint.strokeWidth=(.55f+.35f*scale)*d;paint.alpha=(160*fade).toInt();canvas.drawPath(litPaths,paint)
  val supports=features.filter{it.kind=="night_support"}.sortedWith(compareBy<HazardFeature>{when{womenPolice(it)->0;it.supportKind=="police"->1;it.supportKind=="hospital"->2;it.supportKind=="fuel"->3;else->4}}.thenBy{if(it.id in relevantIds)0 else 1}.thenBy{abs(it.point.latitude-camera.latitude)+abs(it.point.longitude-camera.longitude)})
  for(f in supports){
   if(bubbles.size>=16)break
   if(f.supportKind=="fuel" && route!=null && f.id !in relevantIds)continue
   val kind=if(womenPolice(f))"women_police" else f.supportKind
   val cap=when(kind){"women_police"->4;"police"->5;"hospital"->4;"fuel"->3;else->2}
   if((categoryCounts[kind]?:0)>=cap)continue
   if(abs(f.point.latitude-camera.latitude)>span || abs(f.point.longitude-camera.longitude)>span)continue
   val center=map.project(f.point.latitude,f.point.longitude)?:continue
   if(center.x<18*d || center.x>width-18*d || center.y<185*d || (center.x<205*d && center.y<325*d) || center.y>height-165*d || bubbles.any{hypot(center.x-it.x,center.y-it.y)<42*d})continue
   categoryCounts[kind]=(categoryCounts[kind]?:0)+1;bubbles.add(center)
   drawSupport(canvas,center,kind,selected?.id==f.id);hits.add(f to listOf(center))
  }
  canvas.restore()
  selected?.let{f->hits.firstOrNull{it.first.id==f.id}?.let{(_,pts)->
   val p=pts[pts.size/2];val name=f.title.substringAfter(": ",f.title);val title=if(name.length>31)name.take(30)+"…" else name
   val subtitle=when(f.kind){"night_lit"->"Mapped lit · not live checked";"night_unlit"->"Mapped unlit · check conditions";"night_support"->if(womenPolice(f))"Women’s police · hours unverified" else "Support place · hours unverified";else->"Lighting unknown"}
   paint.style=Paint.Style.FILL;paint.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);paint.textSize=12*d
   val w=(max(paint.measureText(title),paint.measureText(subtitle))+28*d).coerceAtMost(width-24*d)
   val left=(p.x-w/2).coerceIn(12*d,(width-w-12*d).coerceAtLeast(12*d));val top=(p.y-73*d).coerceIn(350*d,(height-200*d).coerceAtLeast(350*d))
   paint.color=Color.rgb(250,249,244);paint.alpha=255;canvas.drawRoundRect(RectF(left,top,left+w,top+53*d),14*d,14*d,paint)
   paint.color=Color.rgb(20,39,53);canvas.drawText(title,left+14*d,top+21*d,paint);paint.textSize=10*d;paint.color=Color.rgb(85,104,116);canvas.drawText(subtitle,left+14*d,top+39*d,paint)
  }}
 }
 private fun womenPolice(f:HazardFeature)=f.supportKind=="police" && (f.title.contains("women",true) || f.title.contains("awpolice",true) || f.title.contains("மகளிர்"))
 private fun drawSupport(canvas:Canvas,p:PointF,kind:String,focused:Boolean){
  canvas.save();canvas.translate(p.x,p.y);paint.style=Paint.Style.FILL;paint.alpha=(255*fade).toInt();paint.color=if(kind=="women_police")Color.rgb(76,34,63) else Color.rgb(20,51,62)
  canvas.drawCircle(0f,0f,(if(focused)18 else 14)*d,paint);paint.style=Paint.Style.STROKE;paint.color=if(kind=="women_police")Color.rgb(255,153,200) else Color.rgb(131,221,206);paint.strokeWidth=1f*d;canvas.drawCircle(0f,0f,14*d,paint)
  paint.strokeWidth=1.5f*d;paint.strokeCap=Paint.Cap.ROUND
  when(kind){
   "hospital","pharmacy"->{canvas.drawLine(-6*d,0f,6*d,0f,paint);canvas.drawLine(0f,-6*d,0f,6*d,paint)}
   "police","women_police"->{val shield=Path().apply{moveTo(0f,-7*d);lineTo(6*d,-4*d);lineTo(5*d,3*d);quadTo(2*d,7*d,0f,8*d);quadTo(-2*d,7*d,-5*d,3*d);lineTo(-6*d,-4*d);close()};canvas.drawPath(shield,paint)}
   "fuel"->{canvas.drawRoundRect(RectF(-5*d,-7*d,3*d,7*d),1*d,1*d,paint);canvas.drawLine(-4*d,-2*d,2*d,-2*d,paint);canvas.drawLine(4*d,-3*d,7*d,1*d,paint);canvas.drawLine(7*d,1*d,7*d,5*d,paint);canvas.drawArc(RectF(3*d,3*d,7*d,7*d),0f,180f,false,paint);canvas.drawLine(-7*d,8*d,5*d,8*d,paint)}
   else->{canvas.drawCircle(0f,0f,5*d,paint)}
  };canvas.restore()
 }
 fun destroy(){animator?.cancel();selectionScope.cancel()}
}
