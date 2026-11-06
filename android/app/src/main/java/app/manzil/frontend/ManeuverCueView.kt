package app.manzil.frontend

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View

/** Schematic maneuver pictogram, never a geographic road overlay. */
class ManeuverCueView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var kind = "unknown"
    private var saved = true
    private var phase = 1f
    private var animator: ValueAnimator? = null
    fun show(kind: String, saved: Boolean, changed: Boolean) {
        if (!changed && this.kind == kind && this.saved == saved) return
        this.kind = kind; this.saved = saved
        contentDescription = if(saved) "Saved maneuver symbol" else "$kind maneuver symbol from Maps"
        animator?.cancel(); phase = 1f
        if(changed && !saved && isShown && ValueAnimator.areAnimatorsEnabled()) {
            animator = ValueAnimator.ofFloat(0f,1f).apply {
                duration = 650
                addUpdateListener { phase = it.animatedValue as Float; invalidate() }
                start()
            }
        }
        invalidate()
    }
    override fun onDetachedFromWindow() { animator?.cancel(); super.onDetachedFromWindow() }
    override fun onDraw(canvas: Canvas) {
        val side=minOf(width,height).toFloat()
        canvas.save(); canvas.translate((width-side)/2,(height-side)/2); canvas.scale(side/88,side/88)
        val color=if(saved) Color.rgb(140,154,172) else Color.rgb(45,199,190)
        paint.color=color; paint.alpha=22; paint.style=Paint.Style.FILL
        canvas.drawRoundRect(0f,0f,88f,88f,24f,24f,paint)
        paint.alpha=255; paint.style=Paint.Style.STROKE; paint.strokeWidth=5f; paint.strokeCap=Paint.Cap.ROUND; paint.strokeJoin=Paint.Join.ROUND
        val path=Path()
        when(kind) {
            "left","right" -> {
                if(kind=="left") { canvas.translate(88f,0f); canvas.scale(-1f,1f) }
                path.moveTo(30f,66f); path.lineTo(30f,40f); path.quadTo(30f,27f,43f,27f); path.lineTo(64f,27f)
            }
            "straight" -> { path.moveTo(44f,67f);path.lineTo(44f,22f) }
            "uturn" -> { path.moveTo(62f,67f);path.lineTo(62f,34f);path.cubicTo(62f,12f,26f,12f,26f,34f);path.lineTo(26f,52f) }
            "roundabout" -> { path.addCircle(44f,40f,17f,Path.Direction.CW);path.moveTo(44f,57f);path.lineTo(44f,71f) }
            else -> { path.addCircle(44f,38f,14f,Path.Direction.CW);path.moveTo(35f,49f);path.lineTo(44f,64f);path.lineTo(53f,49f) }
        }
        paint.alpha=65;canvas.drawPath(path,paint);paint.alpha=255
        val segment=Path(); val measure=PathMeasure(path,false); measure.getSegment(0f,measure.length*phase,segment,true);canvas.drawPath(segment,paint)
        if(phase>.75f) {
            val arrow=Path()
            when(kind) {
                "left","right" -> { arrow.moveTo(55f,18f);arrow.lineTo(65f,27f);arrow.lineTo(55f,36f) }
                "straight" -> {arrow.moveTo(34f,32f);arrow.lineTo(44f,22f);arrow.lineTo(54f,32f)}
                "uturn" -> {arrow.moveTo(17f,43f);arrow.lineTo(26f,53f);arrow.lineTo(35f,43f)}
            }
            canvas.drawPath(arrow,paint)
        }
        canvas.restore()
    }
}
