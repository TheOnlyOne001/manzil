package app.manzil.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.core.content.ContextCompat
import app.manzil.R
import kotlin.math.roundToInt

/** 56dp round panda bubble with a small status dot. Static drawing; no looping animation. */
class PandaBubbleView(context: Context) : View(context) {
    private var pulse = 0f
    private var pulseAnimator: android.animation.ValueAnimator? = null
    fun signalUpdate() {
        if (!isShown || !android.animation.ValueAnimator.areAnimatorsEnabled()) return
        pulseAnimator?.cancel()
        pulseAnimator = android.animation.ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 700
            addUpdateListener { pulse = it.animatedValue as Float; invalidate() }
            start()
        }
    }
    override fun onDetachedFromWindow() { pulseAnimator?.cancel(); super.onDetachedFromWindow() }
    private val density = resources.displayMetrics.density
    val sizePx: Int = (56 * density).roundToInt()

    private val panda: Drawable = requireNotNull(ContextCompat.getDrawable(context, R.drawable.panda))
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dotRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.surface)
    }

    /** Teal when a saved journey is in progress, muted otherwise. Colour is never the only signal. */
    var active: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    init {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(context, R.color.surface))
            setStroke((1.5f * density).roundToInt(), ContextCompat.getColor(context, R.color.border))
        }
        elevation = 4 * density
        isClickable = true
        isFocusable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(sizePx, sizePx)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pulse > 0f) {
            val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 3 * density
                color = android.graphics.Color.rgb(20, 175, 164); alpha = (pulse * 220).toInt()
            }
            canvas.drawCircle(width / 2f, height / 2f, width / 2f - 2 * density, halo)
        }
        val inset = (7 * density).roundToInt()
        panda.setBounds(inset, inset, width - inset, height - inset)
        panda.draw(canvas)

        val r = 5.5f * density
        val cx = width - r - 2 * density
        val cy = height - r - 2 * density
        canvas.drawCircle(cx, cy, r + 2 * density, dotRing)
        dotPaint.color = ContextCompat.getColor(context, if (active) R.color.teal else R.color.muted)
        canvas.drawCircle(cx, cy, r, dotPaint)
    }
}
