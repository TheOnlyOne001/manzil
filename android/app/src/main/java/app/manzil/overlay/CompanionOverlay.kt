package app.manzil.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import app.manzil.R
import app.manzil.system.AppSettings
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** What the bubble panel shows. Built from saved state only. */
data class OverlayModel(
    val hasJourney: Boolean,
    val stepLabel: String,
    val title: String,
    val status: String,
    val canGoNext: Boolean,
    val isCurrent: Boolean = false,
)

/**
 * Plain Android View overlay (TYPE_APPLICATION_OVERLAY). Collapsed: a draggable 56dp panda.
 * Tap: a compact panel with Next / Repeat / Open Manzil / Close. Never focusable, so it never
 * takes the keyboard from the app underneath, and clamps itself inside the system-bar-safe area.
 */
@SuppressLint("ClickableViewAccessibility")
class CompanionOverlay(
    private val context: Context,
    private val settings: AppSettings,
    private val actions: Actions,
) {
    interface Actions {
        fun onNext()
        fun onRepeat()
        fun onOpenApp()
        fun onClose()
    }

    private val wm = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float): Int = (v * density).roundToInt()
    private fun color(@ColorRes id: Int) = ContextCompat.getColor(context, id)

    private val windowPad = dp(6f)
    private val gap = dp(8f)
    private val edgeMargin = dp(8f)

    private val manrope: Typeface? = try {
        ResourcesCompat.getFont(context, R.font.manrope)
    } catch (e: Exception) {
        null
    }

    private val bubble = PandaBubbleView(context)
    private val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(windowPad, windowPad, windowPad, windowPad)
        clipToPadding = false
    }

    private val statusText = text(12f, R.color.muted, 600)
    private val stepText = text(12f, R.color.primary, 700).apply { letterSpacing = 0.04f }
    private val titleText = text(18f, R.color.ink, 700).apply {
        maxLines = 4
        ellipsize = TextUtils.TruncateAt.END
    }
    private val messageText = text(14f, R.color.ink, 500).apply {
        visibility = View.GONE
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val nextButton = actionButton(R.drawable.ic_chevron_right, R.string.overlay_next, primary = true) { actions.onNext() }
    private val repeatButton = actionButton(R.drawable.ic_rotate_ccw, R.string.overlay_repeat, primary = false) { actions.onRepeat() }
    private val openButton = actionButton(R.drawable.ic_route, R.string.overlay_open_app, primary = false) { actions.onOpenApp() }
    private val closeButton = actionButton(R.drawable.ic_x, R.string.overlay_close, primary = false) { actions.onClose() }

    private val panel = app.manzil.frontend.CompanionOverlayView(context).apply {
        onNext = { actions.onNext() }
        onRead = { actions.onRepeat() }
        onOpen = { actions.onOpenApp() }
        onCollapse = { collapse() }
        onDismiss = { actions.onClose() }
    }

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = "Manzil companion"
        if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= 30) {
            setFitInsetsTypes(0)
        }
    }

    private var attached = false
    private var expanded = false
    private var collapsedX = 0
    private var collapsedY = 0
    private var snapAnimator: ValueAnimator? = null

    private val collapsedWindowSize get() = bubble.sizePx + 2 * windowPad

    init {
        bubble.contentDescription = context.getString(R.string.overlay_bubble_cd)
        bubble.setOnClickListener { if (expanded) collapse() else expand() }
        bubble.setOnTouchListener(DragListener())
        root.addView(bubble, LinearLayout.LayoutParams(bubble.sizePx, bubble.sizePx))
        updateBubbleState()
    }

    fun show() {
        if (attached) return
        val safe = safeBounds()
        val saved = settings.bubblePosition()
        params.x = saved?.first ?: (safe.left + edgeMargin)
        params.y = saved?.second ?: (safe.top + (safe.height() * 0.35f).roundToInt())
        clamp(collapsedWindowSize, collapsedWindowSize, safe)
        wm.addView(root, params)
        attached = true
    }

    fun remove() {
        snapAnimator?.cancel()
        if (attached) {
            wm.removeViewImmediate(root)
            attached = false
        }
    }

    private var lastObservedTitle: String? = null
    fun render(model: OverlayModel) {
        val maneuverText = app.manzil.frontend.ManeuverCue.parse(model.title).instruction
        if (lastObservedTitle != maneuverText && model.isCurrent) bubble.signalUpdate()
        lastObservedTitle = maneuverText
        panel.renderLabel(model.title, model.stepLabel, model.canGoNext, model.isCurrent)
        statusText.text = model.status
        stepText.text = model.stepLabel
        titleText.text = model.title
        setEnabled(nextButton, model.canGoNext)
        setEnabled(repeatButton, model.hasJourney)
        bubble.active = model.hasJourney
        if (expanded) relayoutExpanded()
    }

    /** One-line feedback from the last tool result (e.g. "You're on the last saved step."). */
    fun showMessage(message: String?) {
        panel.feedback(message)
        messageText.text = message.orEmpty()
        messageText.visibility = if (message.isNullOrBlank()) View.GONE else View.VISIBLE
        if (expanded) relayoutExpanded()
    }

    fun collapseIfExpanded() {
        if (expanded) collapse()
    }

    fun onConfigurationChanged() {
        if (!attached) return
        if (expanded) {
            collapse()
        } else {
            clamp(collapsedWindowSize, collapsedWindowSize, safeBounds())
            wm.updateViewLayout(root, params)
        }
    }

    // ---- expand / collapse -------------------------------------------------------------------

    private fun panelWidth(safe: Rect): Int = min(dp(296f), safe.width() - 2 * edgeMargin - 2 * windowPad)

    private fun expand() {
        if (!attached) return
        snapAnimator?.cancel()
        collapsedX = params.x
        collapsedY = params.y
        expanded = true
        messageText.visibility = if (messageText.text.isNullOrBlank()) View.GONE else View.VISIBLE
        relayoutExpanded()
        updateBubbleState()
    }

    private fun relayoutExpanded() {
        val safe = safeBounds()
        val pw = panelWidth(safe)
        panel.measure(
            View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val ph = panel.measuredHeight
        val bubbleCenterX = collapsedX + collapsedWindowSize / 2
        val bubbleCenterY = collapsedY + collapsedWindowSize / 2
        val onRight = bubbleCenterX > safe.centerX()
        val above = bubbleCenterY > safe.centerY()

        root.removeAllViews()
        root.addView(panel, LinearLayout.LayoutParams(pw, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.gravity = if (onRight) Gravity.END else Gravity.START
        val windowW = pw + 2 * windowPad
        val windowH = ph + 2 * windowPad
        params.x = if (onRight) collapsedX + collapsedWindowSize - windowW else collapsedX
        params.y = if (above) collapsedY + collapsedWindowSize - windowH else collapsedY
        clamp(windowW, windowH, safe)
        if (attached) wm.updateViewLayout(root, params)
    }

    private fun collapse() {
        expanded = false
        root.removeAllViews()
        root.gravity = Gravity.START
        root.addView(bubble, LinearLayout.LayoutParams(bubble.sizePx, bubble.sizePx))
        params.x = collapsedX
        params.y = collapsedY
        clamp(collapsedWindowSize, collapsedWindowSize, safeBounds())
        if (attached) wm.updateViewLayout(root, params)
        updateBubbleState()
    }

    private fun updateBubbleState() {
        if (Build.VERSION.SDK_INT >= 30) {
            bubble.stateDescription = context.getString(
                if (expanded) R.string.overlay_state_expanded else R.string.overlay_state_collapsed,
            )
        }
    }

    // ---- geometry ----------------------------------------------------------------------------

    /** Screen area excluding status/navigation bars and display cutouts, in window coordinates. */
    private fun safeBounds(): Rect {
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = wm.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
            val b = metrics.bounds
            return Rect(insets.left, insets.top, b.width() - insets.right, b.height() - insets.bottom)
        }
        val dm = context.resources.displayMetrics
        @Suppress("DEPRECATION")
        val real = android.util.DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
        val statusId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val status = if (statusId > 0) context.resources.getDimensionPixelSize(statusId) else dp(24f)
        val width = if (real.widthPixels > 0) real.widthPixels else dm.widthPixels
        val height = if (real.heightPixels > 0) real.heightPixels else dm.heightPixels
        return Rect(0, status, width, height - dp(48f))
    }

    private fun clamp(windowW: Int, windowH: Int, safe: Rect) {
        val minX = safe.left + edgeMargin - windowPad
        val maxX = safe.right - edgeMargin + windowPad - windowW
        val minY = safe.top + edgeMargin - windowPad
        val maxY = safe.bottom - edgeMargin + windowPad - windowH
        params.x = if (maxX < minX) minX else params.x.coerceIn(minX, maxX)
        params.y = if (maxY < minY) minY else params.y.coerceIn(minY, maxY)
    }

    private fun snapToEdge() {
        val safe = safeBounds()
        val centerX = params.x + collapsedWindowSize / 2
        val target = if (centerX < safe.centerX()) {
            safe.left + edgeMargin - windowPad
        } else {
            safe.right - edgeMargin + windowPad - collapsedWindowSize
        }
        snapAnimator?.cancel()
        // ValueAnimator honours the system animator duration scale (0 = reduced motion: instant).
        snapAnimator = ValueAnimator.ofInt(params.x, target).apply {
            duration = 180
            addUpdateListener {
                params.x = it.animatedValue as Int
                if (attached) wm.updateViewLayout(root, params)
            }
            start()
        }
        settings.saveBubblePosition(target, params.y)
    }

    private inner class DragListener : View.OnTouchListener {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            if (expanded) return false // tap-to-collapse only; no dragging the open panel
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    snapAnimator?.cancel()
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    v.isPressed = true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        v.isPressed = false
                    }
                    if (dragging) {
                        params.x = startX + dx.roundToInt()
                        params.y = startY + dy.roundToInt()
                        clamp(collapsedWindowSize, collapsedWindowSize, safeBounds())
                        if (attached) wm.updateViewLayout(root, params)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    if (dragging) snapToEdge() else v.performClick()
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    if (dragging) snapToEdge()
                }
            }
            return true
        }
    }

    // ---- view builders -----------------------------------------------------------------------

    private fun text(sizeSp: Float, @ColorRes colorRes: Int, weight: Int) = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color(colorRes))
        manrope?.let { typeface = it }
        setFontVariationSettings("'wght' $weight")
        includeFontPadding = false
        setLineSpacing(dp(2f).toFloat(), 1f)
    }

    private fun actionButton(
        @DrawableRes icon: Int,
        labelRes: Int,
        primary: Boolean,
        onClick: () -> Unit,
    ): LinearLayout {
        val fg = color(if (primary) R.color.surface else R.color.primary)
        val bg = color(if (primary) R.color.primary else R.color.primary_soft)
        val label = context.getString(labelRes)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            minimumHeight = dp(56f)
            minimumWidth = dp(48f)
            setPadding(dp(4f), dp(8f), dp(4f), dp(8f))
            val shape = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                setColor(bg)
            }
            val mask = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                setColor(0xFF000000.toInt())
            }
            background = RippleDrawable(ColorStateList.valueOf(0x33142D4E), shape, mask)
            isClickable = true
            isFocusable = true
            contentDescription = label
            setOnClickListener { onClick() }
            addView(
                ImageView(context).apply {
                    setImageResource(icon)
                    imageTintList = ColorStateList.valueOf(fg)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LinearLayout.LayoutParams(dp(24f), dp(24f)),
            )
            addView(
                text(12f, if (primary) R.color.surface else R.color.primary, 700).apply {
                    text = label
                    gravity = Gravity.CENTER
                    maxLines = 2
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(4f)
                },
            )
        }
    }

    private fun setEnabled(button: View, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.45f
    }

    private fun linearParams(top: Float) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(top) }

    private fun weighted(start: Float = 0f) = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
        marginStart = dp(start)
    }
}
