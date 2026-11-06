package app.manzil.frontend

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Small, opaque, readable surface. Window placement and dragging belong to the service. */
class CompanionOverlayView(context: Context) : LinearLayout(context) {
    private val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    private val ink = if (night) Color.rgb(237, 243, 251) else Color.rgb(20, 45, 78)
    private val muted = if (night) Color.rgb(185, 199, 217) else Color.rgb(83, 101, 122)
    private val blue = Color.rgb(36, 87, 214)
    private val cue = ManeuverCueView(context)
    private val distance = TextView(context)
    private var lastInstruction: String? = null
    private val title = TextView(context)
    private val meta = TextView(context)
    private val next = GlyphView(context, "next")
    private val feedbackText = TextView(context)
    var onNext: () -> Unit = {}
    var onRead: () -> Unit = {}
    var onOpen: () -> Unit = {}
    var onCollapse: () -> Unit = {}
    var onDismiss: () -> Unit = {}

    init {
        orientation = VERTICAL
        setPadding(dp(18), dp(8), dp(10), dp(8))
        background = GradientDrawable().apply {
            setColor(if (night) Color.rgb(23, 35, 51) else Color.WHITE)
            cornerRadius = dp(26).toFloat()
            setStroke(dp(1), if (night) Color.rgb(61, 78, 98) else Color.rgb(222, 229, 237))
        }
        elevation = dp(10).toFloat()
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        val face = PandaFaceView(context).apply {
            contentDescription = "Collapse companion"
            setOnClickListener { onCollapse() }
        }
        header.addView(face, LayoutParams(dp(48), dp(48)))
        meta.apply { textSize = 12f; setTextColor(muted); setPadding(dp(6), 0, 0, 0) }
        header.addView(meta, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(GlyphView(context, "close").apply {
            tint = muted
            contentDescription = "Dismiss companion"
            setOnClickListener { onDismiss() }
        }, LayoutParams(dp(48), dp(48)))
        addView(header)
        title.apply {
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
            textSize = 16f
            setTextColor(ink)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(dp(4), dp(5), dp(12), dp(8))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val maneuverRow = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        maneuverRow.addView(cue, LayoutParams(dp(80), dp(80)).apply { marginEnd = dp(14) })
        val words = LinearLayout(context).apply { orientation = VERTICAL }
        distance.apply { textSize = 28f; setTextColor(ink); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
        words.addView(distance)
        words.addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        maneuverRow.addView(words, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(maneuverRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8); bottomMargin = dp(8) })
        feedbackText.apply { textSize = 12f; setTextColor(muted); visibility = View.GONE; setPadding(dp(4), 0, dp(12), dp(8)) }
        addView(feedbackText)
        val footer = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        footer.addView(GlyphView(context, "read").apply {
            tint = muted
            contentDescription = "Read observation"
            setOnClickListener { onRead() }
        }, LayoutParams(dp(48), dp(48)))
        footer.addView(TextView(context).apply {
            text = "Open Manzil"
            textSize = 13f
            setTextColor(muted)
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(48)
            setPadding(dp(8), 0, 0, 0)
            contentDescription = "Open saved journey"
            setOnClickListener { onOpen() }
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        next.apply {
            contentDescription = "Next saved step"
            background = GradientDrawable().apply { setColor(blue); cornerRadius = dp(24).toFloat() }
            tint = Color.WHITE
            setOnClickListener { onNext() }
        }
        footer.addView(next, LayoutParams(dp(52), dp(48)).apply { marginEnd = dp(4) })
        addView(footer)
    }

    fun render(instruction: String, step: Int, total: Int, isSample: Boolean) {
        title.text = instruction
        meta.text = "${if (isSample) "Sample" else "Saved"} · ${step + 1} of $total"
        next.isEnabled = step < total - 1
        next.alpha = if (next.isEnabled) 1f else .4f
    }

    fun renderLabel(instruction: String, label: String, canGoNext: Boolean, isCurrent: Boolean = false) {
        val parsed = ManeuverCue.parse(instruction)
        val saved = !isCurrent
        val changed = lastInstruction != parsed.instruction
        lastInstruction = parsed.instruction
        title.text = parsed.instruction
        title.contentDescription = instruction
        distance.text = parsed.distance.orEmpty()
        distance.visibility = if (parsed.distance == null) View.GONE else View.VISIBLE
        if (changed) cue.show(parsed.kind, saved, true)
        else cue.show(parsed.kind, saved, false)
        meta.text = label
        next.isEnabled = canGoNext
        next.visibility = if (canGoNext) View.VISIBLE else View.GONE
        next.alpha = if (canGoNext) 1f else .4f
    }

    fun feedback(message: String?) {
        feedbackText.text = message
        feedbackText.visibility = if (message.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}

/** Original vector drawing; no looping motion or image downloads. */
class PandaFaceView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = minOf(width, height).toFloat()
        canvas.save()
        canvas.translate((width - side) / 2, (height - side) / 2)
        canvas.scale(side / 64f, side / 64f)
        val ink = Color.rgb(20, 45, 78)
        p.style = Paint.Style.FILL
        p.color = ink
        canvas.drawCircle(15f, 15f, 8f, p)
        canvas.drawCircle(49f, 15f, 8f, p)
        p.color = Color.WHITE
        canvas.drawOval(9f, 10f, 55f, 56f, p)
        p.color = ink
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f
        canvas.drawOval(9f, 10f, 55f, 56f, p)
        p.style = Paint.Style.FILL
        canvas.save()
        canvas.rotate(25f, 23f, 30f)
        canvas.drawOval(16f, 21f, 29f, 39f, p)
        canvas.restore()
        canvas.save()
        canvas.rotate(-25f, 41f, 30f)
        canvas.drawOval(35f, 21f, 48f, 39f, p)
        canvas.restore()
        p.color = Color.WHITE
        canvas.drawCircle(24f, 28f, 2.5f, p)
        canvas.drawCircle(40f, 28f, 2.5f, p)
        p.color = ink
        canvas.drawOval(29f, 38f, 35f, 42f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.6f
        p.strokeCap = Paint.Cap.ROUND
        canvas.drawArc(27f, 40f, 37f, 47f, 20f, 140f, false, p)
        canvas.restore()
    }
}

private class GlyphView(context: Context, private val glyph: String) : View(context) {
    var tint = Color.rgb(83, 101, 122)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init { isClickable = true; isFocusable = true }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val unit = resources.displayMetrics.density
        canvas.save()
        canvas.translate(width / 2f - 12f * unit, height / 2f - 12f * unit)
        canvas.scale(unit, unit)
        paint.color = tint
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.7f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        val path = Path()
        when (glyph) {
            "next" -> { path.moveTo(5f, 12f); path.lineTo(19f, 12f); path.moveTo(13f, 6f); path.lineTo(19f, 12f); path.lineTo(13f, 18f) }
            "close" -> { path.moveTo(7f, 7f); path.lineTo(17f, 17f); path.moveTo(17f, 7f); path.lineTo(7f, 17f) }
            "read" -> {
                path.moveTo(4f, 9f); path.lineTo(8f, 9f); path.lineTo(12f, 5f); path.lineTo(12f, 19f); path.lineTo(8f, 15f); path.lineTo(4f, 15f); path.close()
                canvas.drawArc(12f, 6f, 21f, 18f, -60f, 120f, false, paint)
            }
        }
        canvas.drawPath(path, paint)
        canvas.restore()
    }
}
