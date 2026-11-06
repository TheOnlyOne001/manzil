package app.manzil.offline

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.provider.Settings
import android.view.View
import kotlin.math.hypot
import java.time.Instant

/** Geographic overlay inside either map renderer; never a screen-position guess over another app. */
class HazardCanvas(context:Context):View(context) {
 var surface:MapSurface?=null
 private var features:List<HazardFeature> = emptyList()
 private var layer=HazardLayer.NONE
 private var selected:HazardFeature?=null
 private var fade=1f
 private var animator:ValueAnimator?=null
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
 private val hits=mutableListOf<Pair<HazardFeature,PointF>>()
 private val d=resources.displayMetrics.density
 fun show(data:List<HazardFeature>,choice:HazardLayer) {
  val animate=choice!=layer
  if(features===data && layer==choice)return
  features=data;layer=choice;selected=null;contentDescription="${choice.label} map overlay. Tap a marked location for its evidence."
  animator?.cancel();fade=1f
  if(animate && Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f){animator=ValueAnimator.ofFloat(0f,1f).apply{duration=950;addUpdateListener{fade=it.animatedValue as Float;invalidate()};start()}}
  invalidate()
 }
 fun tap(point:PointF):Boolean {
  val hit=hits.minByOrNull{hypot(it.second.x-point.x,it.second.y-point.y)}?.takeIf{hypot(it.second.x-point.x,it.second.y-point.y)<28*d}
  if(hit==null){selected=null;invalidate();return false}
  selected=if(selected?.id==hit.first.id)null else hit.first
  contentDescription=selected?.let{"${it.title}. ${it.historicalDepthCm?.let{d->"Recorded depth $d cm. "}.orEmpty()}${it.evidence}. ${it.detail}"}?:layer.label
  invalidate();return true
 }
 override fun onDraw(canvas:Canvas){super.onDraw(canvas);hits.clear();val map=surface?:return;if(layer==HazardLayer.NONE)return
  val zoom=map.camera()?.zoom?:0.0;val now=Instant.now();val visible=features.filter{(it.layer==layer || (layer==HazardLayer.HISTORY && it.layer in listOf(HazardLayer.RISK,HazardLayer.REPORTS))) && it.visibleAt(now)}
  val baseColor=when(layer){HazardLayer.HISTORY->Color.rgb(186,125,38);HazardLayer.REPORTS->Color.rgb(211,83,66);HazardLayer.RISK->Color.rgb(219,157,26);else->Color.rgb(32,130,143)}
  val hotspotDots=mutableListOf<PointF>()
  for(feature in visible){val color=if(feature.layer==HazardLayer.RISK)when(feature.riskBand){"high"->Color.rgb(194,48,78);"elevated"->Color.rgb(235,109,33);else->Color.rgb(219,157,26)} else if(feature.layer==HazardLayer.REPORTS)Color.rgb(211,83,66) else if(feature.kind=="hotspot")Color.rgb(129,84,160) else feature.historicalDepthCm?.let{when{it<10->Color.rgb(219,157,26);it<=30->Color.rgb(235,109,33);else->Color.rgb(194,48,78)}}?:baseColor;val p=map.project(feature.point.latitude,feature.point.longitude)?:continue
   if(p.x< -50*d || p.x>width+50*d || p.y<150*d || p.y>height-210*d)continue
   if(feature.kind=="hotspot" && zoom<13){if(hotspotDots.any{hypot(it.x-p.x,it.y-p.y)<12*d})continue;hotspotDots.add(p)}
   val active=selected?.id==feature.id
   if(zoom>=10.5 && feature.road.size>=2){val path=Path();feature.road.forEachIndexed{i,pt->map.project(pt.latitude,pt.longitude)?.let{if(i==0)path.moveTo(it.x,it.y)else path.lineTo(it.x,it.y)}}
    paint.style=Paint.Style.STROKE;paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND;paint.color=color
    paint.alpha=(fade*if(active)80 else 45).toInt();paint.strokeWidth=if(active)12*d else if(zoom<13)5*d else 8*d;paint.pathEffect=null;canvas.drawPath(path,paint)
    paint.alpha=(fade*if(active)230 else 160).toInt();paint.strokeWidth=if(zoom<13)1.8f*d else if(feature.historicalDepthCm!=null || layer==HazardLayer.RISK)4*d else 2*d;paint.pathEffect=if(feature.historicalDepthCm!=null || layer==HazardLayer.RISK)null else DashPathEffect(floatArrayOf(7*d,4*d),0f);canvas.drawPath(path,paint);paint.pathEffect=null
   }
   paint.style=Paint.Style.FILL;paint.color=color;paint.alpha=(fade*36).toInt();canvas.drawCircle(p.x,p.y,if(active)14*d else if(zoom<14)6*d else 10*d,paint)
   paint.alpha=(fade*240).toInt();if(feature.kind=="hotspot"){canvas.save();canvas.rotate(45f,p.x,p.y);val r=if(zoom<14)3*d else 5*d;canvas.drawRect(p.x-r,p.y-r,p.x+r,p.y+r,paint);canvas.restore()}else canvas.drawCircle(p.x,p.y,if(zoom<14)3*d else 5*d,paint)
   paint.style=Paint.Style.STROKE;paint.color=Color.WHITE;paint.strokeWidth=1.5f*d;if(feature.kind=="hotspot"){canvas.save();canvas.rotate(45f,p.x,p.y);val r=if(zoom<14)3*d else 5*d;canvas.drawRect(p.x-r,p.y-r,p.x+r,p.y+r,paint);canvas.restore()}else canvas.drawCircle(p.x,p.y,if(zoom<14)3*d else 5*d,paint);paint.style=Paint.Style.FILL
   hits.add(feature to p)
  }
  selected?.let{feature->hits.firstOrNull{it.first.id==feature.id}?.second?.let{p->
   val title=feature.historicalDepthCm?.let{"Recorded depth · ${String.format(java.util.Locale.US,"%.1f",it)} cm"}?:feature.title.take(35);val subtitle=when(feature.evidence){"historical"->if(feature.id.startsWith("flood2015-"))"2015 record · depth unknown · not live" else if(feature.kind=="hotspot")"GCC · 2020 hotspot · not live" else "GCC survey · date unknown · not live";"modelled"->"Rainfall proxy · low confidence";"reported"->"Citizen report · unverified";else->"Mapped place · access unknown"}
   paint.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);paint.textSize=12*d
   val w=maxOf(paint.measureText(title),paint.measureText(subtitle)).coerceAtMost(width-48*d)+24*d
   val left=(p.x-w/2).coerceIn(12*d,(width-w-12*d).coerceAtLeast(12*d));val top=(p.y-70*d).coerceAtLeast(155*d)
   paint.color=Color.WHITE;paint.alpha=250;canvas.drawRoundRect(RectF(left,top,left+w,top+51*d),12*d,12*d,paint)
   paint.color=Color.rgb(20,45,78);paint.alpha=255;canvas.drawText(title,left+12*d,top+20*d,paint)
   paint.textSize=10*d;paint.color=Color.rgb(95,111,124);canvas.drawText(subtitle,left+12*d,top+37*d,paint)
  }}
 }
 fun destroy(){animator?.cancel()}
}
