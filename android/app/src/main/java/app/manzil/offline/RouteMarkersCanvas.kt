package app.manzil.offline

import android.content.Context
import android.graphics.*
import android.view.View
import app.manzil.offline.routing.OfflineRoute
import kotlin.math.*

/** Geographic route symbols, shared by both renderers. Start is not a simulated GPS position. */
class RouteMarkersCanvas(context:Context):View(context) {
 var surface:MapSurface?=null
 var route:OfflineRoute?=null
 private val d=resources.displayMetrics.density
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
 override fun onDraw(canvas:Canvas) {
  super.onDraw(canvas)
  val map=surface?:return;val r=route?:return
  if(r.points.size<2)return
  val pts=r.points.mapNotNull{map.project(it.latitude,it.longitude)}
  if(pts.size<2)return
  paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND
  var spacing=0f;var count=0
  for(i in 1 until pts.size){val a=pts[i-1];val b=pts[i];val length=hypot(b.x-a.x,b.y-a.y)
   if(length<1f)continue
   var at=90*d-spacing
   while(at<length && count<30){val x=a.x+(b.x-a.x)*at/length;val y=a.y+(b.y-a.y)*at/length
    if(x in 0f..width.toFloat() && y>170*d && y<height-220*d){
     canvas.save();canvas.translate(x,y);canvas.rotate(Math.toDegrees(atan2((b.y-a.y).toDouble(),(b.x-a.x).toDouble())).toFloat()+90)
     val chevron=Path().apply{moveTo(-3*d,3*d);lineTo(0f,-2*d);lineTo(3*d,3*d)}
     paint.style=Paint.Style.STROKE;paint.color=Color.WHITE;paint.alpha=220;paint.strokeWidth=2*d;canvas.drawPath(chevron,paint);canvas.restore();count++
    };at+=90*d
   };spacing=(spacing+length)%(90*d)
  }
  val start=pts.first();val next=pts.drop(1).firstOrNull{hypot(it.x-start.x,it.y-start.y)>2*d}?:pts.last()
  if(start.x in -40*d..width+40*d && start.y in 0f..height.toFloat()){
   canvas.save();canvas.translate(start.x,start.y)
   paint.style=Paint.Style.FILL;paint.color=Color.WHITE;paint.alpha=255;canvas.drawCircle(0f,0f,19*d,paint)
   paint.color=Color.rgb(224,234,255);canvas.drawCircle(0f,0f,16*d,paint)
   canvas.save();canvas.rotate(Math.toDegrees(atan2((next.y-start.y).toDouble(),(next.x-start.x).toDouble())).toFloat()+90)
   val arrow=Path().apply{moveTo(0f,-12*d);lineTo(10*d,10*d);lineTo(0f,5*d);lineTo(-10*d,10*d);close()}
   paint.color=Color.rgb(36,87,214);canvas.drawPath(arrow,paint);canvas.restore()
   paint.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);paint.textSize=10*d;paint.textAlign=Paint.Align.CENTER
   paint.color=Color.WHITE;canvas.drawRoundRect(RectF(-20*d,23*d,20*d,39*d),6*d,6*d,paint)
   paint.color=Color.rgb(20,45,78);canvas.drawText("Start",0f,35*d,paint);paint.textAlign=Paint.Align.LEFT;canvas.restore()
  }
  val end=pts.last()
  if(end.x in -40*d..width+40*d && end.y in 0f..height.toFloat()){
   canvas.save();canvas.translate(end.x,end.y)
   val pin=Path().apply{moveTo(0f,0f);cubicTo(-6*d,-9*d,-15*d,-17*d,-15*d,-25*d);cubicTo(-15*d,-44*d,15*d,-44*d,15*d,-25*d);cubicTo(15*d,-17*d,6*d,-9*d,0f,0f);close()}
   paint.style=Paint.Style.STROKE;paint.strokeWidth=4*d;paint.color=Color.WHITE;paint.alpha=255;canvas.drawPath(pin,paint)
   paint.style=Paint.Style.FILL;paint.color=Color.rgb(20,45,78);canvas.drawPath(pin,paint)
   paint.color=Color.WHITE;canvas.drawCircle(0f,-26*d,6*d,paint);canvas.restore()
  }
  contentDescription="Route start arrow and arrival pin. Start is the route origin, not live GPS."
 }
}
