package com.gamex.devicetester.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.gamex.devicetester.R
import kotlin.math.min

fun Context.gradientBg(
    colors: IntArray,
    radiusDp: Float,
    orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.LEFT_RIGHT,
    strokeColor: Int? = null,
): GradientDrawable = GradientDrawable(orientation, colors).apply {
    cornerRadius = dpf(radiusDp)
    if (strokeColor != null) setStroke(dp(1), strokeColor)
}

/** Glassy card: theme card colour with a soft tinted outline. */
fun Context.glassCard(tint: Int = Palette.ACCENT, radiusDp: Float = 20f): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = roundedBg(Palette.CARD, dpf(radiusDp), Palette.alpha(tint, if (Palette.dark) 90 else 60), dp(1))
    setPadding(dp(14), dp(14), dp(14), dp(14))
    elevation = if (Palette.dark) 0f else dpf(2f)
}

/** Emoji icon inside a tinted rounded square. */
fun Context.iconBadge(emoji: String, tint: Int, sizeDp: Int = 42, textSp: Float = 20f): TextView =
    label(emoji, textSp).apply {
        gravity = Gravity.CENTER
        background = roundedBg(Palette.alpha(tint, if (Palette.dark) 55 else 38), dpf(sizeDp * 0.32f))
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

/** Round icon button (back, settings, theme toggle…). */
fun Context.circleButton(text: String, sizeDp: Int = 44, onClick: (View) -> Unit): TextView =
    label(text, 18f, Palette.TEXT, true).apply {
        gravity = Gravity.CENTER
        background = roundedBg(Palette.CARD, dpf(sizeDp / 2f), Palette.STROKE, dp(1))
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        isClickable = true
        setOnClickListener(onClick)
    }

/** Section title with an accent bar and optional right-side action. */
fun Context.sectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val bar = View(context).apply { background = roundedBg(Palette.ACCENT, dpf(2f)) }
        addView(bar, LinearLayout.LayoutParams(dp(4), dp(22)).apply { marginEnd = dp(10) })
        addView(label(title, 19f, Palette.TEXT, true), LinearLayout.LayoutParams(0, WRAP, 1f))
        if (action != null) {
            addView(label("$action  ›", 13f, Palette.ACCENT, true).apply {
                setOnClickListener { onAction?.invoke() }
            })
        }
    }

/** "Mobile Testing" logo text with the second word in a cyan → purple gradient. */
fun Context.brandTitle(sizeSp: Float = 24f): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    addView(label("Mobile ", sizeSp, Palette.TEXT, true))
    val t = label("Testing", sizeSp, Palette.TEXT, true)
    val w = t.paint.measureText("Testing")
    t.paint.shader = LinearGradient(0f, 0f, w, 0f, intArrayOf(Palette.CYAN, Palette.ACCENT, Palette.PURPLE), null, Shader.TileMode.CLAMP)
    addView(t)
}

fun Context.shieldIcon(tint: Int, sizeDp: Int): ImageView = ImageView(this).apply {
    setImageResource(R.drawable.ic_shield)
    imageTintList = ColorStateList.valueOf(tint)
    layoutParams = FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
}

/** Pill-shaped status badge. */
fun Context.pill(text: String, color: Int, filled: Boolean): TextView = label(text, 12f, if (filled) Color.WHITE else color, true).apply {
    gravity = Gravity.CENTER
    setPadding(dp(12), dp(6), dp(12), dp(6))
    background = if (filled) roundedBg(color, dpf(14f)) else roundedBg(Palette.alpha(color, 40), dpf(14f), Palette.alpha(color, 120), dp(1))
}

/** Animated circular progress ring with a cyan → blue → purple sweep. */
class ProgressRing(ctx: Context) : View(ctx) {
    private var value = 0f
    private val stroke = ctx.dpf(14f)
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        color = Palette.alpha(Palette.ACCENT, if (Palette.dark) 45 else 30)
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke * 2.2f
        strokeCap = Paint.Cap.ROUND
        alpha = 50
    }
    private val rect = RectF()
    private var animator: ValueAnimator? = null

    fun setProgress(fraction: Float, animate: Boolean = true) {
        val target = fraction.coerceIn(0f, 1f)
        animator?.cancel()
        if (!animate) {
            value = target
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(value, target).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                value = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val pad = stroke * 1.3f
        val size = min(w, h) - 2 * pad
        rect.set((w - size) / 2f, (h - size) / 2f, (w + size) / 2f, (h + size) / 2f)
        val sweep = SweepGradient(w / 2f, h / 2f, intArrayOf(Palette.CYAN, Palette.ACCENT, Palette.PURPLE, Palette.CYAN), null)
        val m = Matrix().apply { postRotate(-90f, w / 2f, h / 2f) }
        sweep.setLocalMatrix(m)
        arc.shader = sweep
        glow.shader = sweep
        glow.alpha = if (Palette.dark) 60 else 30
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawArc(rect, 0f, 360f, false, track)
        if (value > 0f) {
            canvas.drawArc(rect, -90f, 360f * value, false, glow)
            canvas.drawArc(rect, -90f, 360f * value, false, arc)
        }
    }
}

/** Generic smartphone illustration (drawn, no brand artwork). */
class PhoneArtView(ctx: Context) : View(ctx) {
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val screen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lens = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val bw = w * 0.86f
        val l = (w - bw) / 2f
        val r = bw * 0.16f
        body.shader = LinearGradient(0f, 0f, w, h, intArrayOf(Color.rgb(120, 126, 150), Color.rgb(46, 50, 68)), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(l, 0f, l + bw, h), r, r, body)
        val inset = bw * 0.045f
        screen.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(Palette.PURPLE, Color.rgb(30, 27, 75), Palette.CYAN), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(l + inset, inset, l + bw - inset, h - inset), r * 0.8f, r * 0.8f, screen)
        // camera punch-hole + notch pill
        lens.color = Color.rgb(10, 10, 20)
        val pillW = bw * 0.28f
        canvas.drawRoundRect(RectF(w / 2f - pillW / 2, inset * 2.2f, w / 2f + pillW / 2, inset * 2.2f + bw * 0.09f), bw * 0.05f, bw * 0.05f, lens)
        ring.color = Color.argb(70, 255, 255, 255)
        ring.strokeWidth = context.dpf(1f)
        canvas.drawRoundRect(RectF(l + 1, 1f, l + bw - 1, h - 1), r, r, ring)
    }
}
