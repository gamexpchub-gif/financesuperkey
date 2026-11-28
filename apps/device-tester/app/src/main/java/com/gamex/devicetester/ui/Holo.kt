package com.gamex.devicetester.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private fun now(): Float = SystemClock.uptimeMillis() / 1000f

/** Animated sci-fi visual shown at the top of a test (equalizer, scanner, radar…). */
class HoloView(ctx: Context, private val mode: Int) : View(ctx) {
    companion object {
        const val NONE = 0
        const val EQUALIZER = 1
        const val WAVE = 2
        const val FINGER = 3
        const val FACE = 4
        const val RADAR = 5
        const val ORBIT = 6
        const val BATTERY = 8
        const val PULSE = 9

        fun modeFor(testId: String): Int = when {
            testId.startsWith("speaker_") || testId == "headset" -> EQUALIZER
            testId.startsWith("mic_") -> WAVE
            testId == "fingerprint" -> FINGER
            testId == "face" -> FACE
            testId in setOf("sim", "imei", "wifi", "bluetooth", "gps", "nfc") -> RADAR
            testId == "battery" -> BATTERY
            testId in setOf("all_sensors", "proximity") -> ORBIT
            testId in setOf("light", "accel", "gyro", "compass", "barometer", "steps") -> WAVE
            testId in setOf("vibration", "buttons", "flash", "storage", "security") -> PULSE
            else -> NONE
        }
    }

    /** 0..1 activity level (louder sound, stronger signal…). */
    var level = 0.2f
    var battery = -1
    private var success = false
    private var failed = false
    private val wave = FloatArray(120)
    private var head = 0
    private var fed = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val g = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val rnd = java.util.Random(11)
    private val blips = ArrayList<FloatArray>()
    private var lastBlip = 0f
    private val path = Path()
    private val rect = RectF()
    private val matrix = Matrix()

    fun push(v: Float) {
        wave[head] = v.coerceIn(-1f, 1f)
        head = (head + 1) % wave.size
        fed = true
    }

    fun success() {
        success = true
        failed = false
    }

    fun fail() {
        failed = true
    }

    private val c1 get() = when {
        success -> Palette.PASS
        failed -> Palette.FAIL
        else -> Palette.CYAN
    }
    private val c2 get() = when {
        success -> Color.rgb(52, 211, 153)
        failed -> Color.rgb(244, 114, 182)
        else -> Palette.PURPLE
    }

    private fun glowStroke(color: Int, width: Float, draw: (Paint) -> Unit) {
        g.shader = null
        g.style = Paint.Style.STROKE
        g.strokeCap = Paint.Cap.ROUND
        g.strokeJoin = Paint.Join.ROUND
        g.strokeWidth = width * 3.2f
        g.color = Palette.alpha(color, if (Palette.dark) 55 else 35)
        draw(g)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = width
        p.color = color
        draw(p)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val t = now()
        when (mode) {
            EQUALIZER -> equalizer(canvas, t)
            WAVE -> waveform(canvas, t)
            FINGER -> finger(canvas, t)
            FACE -> face(canvas, t)
            RADAR -> radar(canvas, t)
            ORBIT -> orbit(canvas, t)
            BATTERY -> batteryArt(canvas, t)
            PULSE -> pulse(canvas, t)
        }
        if (mode != NONE) postInvalidateOnAnimation()
    }

    private fun equalizer(c: Canvas, t: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        val n = 26
        val gap = w * 0.012f
        val bw = (w - gap * (n + 1)) / n
        val seg = context.dpf(4f)
        val segGap = context.dpf(2f)
        val amp = 0.18f + 0.82f * level.coerceIn(0f, 1f)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, h, 0f, 0f, intArrayOf(c1, Palette.ACCENT, c2), null, Shader.TileMode.CLAMP)
        for (i in 0 until n) {
            val v = abs(sin(t * (2.6f + (i % 5) * 0.9f) + i * 1.7f) * cos(t * 1.3f + i * 0.6f))
            val barH = (0.08f + 0.92f * v * amp) * h * 0.92f
            val x = gap + i * (bw + gap)
            var y = h
            while (y > h - barH) {
                c.drawRect(x, y - seg, x + bw, y, p)
                y -= seg + segGap
            }
        }
        p.shader = null
        level = max(0.18f, level * 0.995f)
    }

    private fun waveform(c: Canvas, t: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        val mid = h / 2
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f
        p.color = Palette.alpha(Palette.ACCENT, 40)
        for (k in 1..3) c.drawLine(0f, h * k / 4f, w, h * k / 4f, p)
        path.reset()
        val n = wave.size
        for (i in 0 until n) {
            val x = w * i / (n - 1)
            val v = if (fed) wave[(head + i) % n] else {
                (sin(i * 0.22f + t * 5f) * 0.45f + sin(i * 0.07f - t * 2f) * 0.3f) * (0.4f + 0.6f * level)
            }
            val y = mid - v * h * 0.42f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        glowStroke(c1, context.dpf(2.5f)) { paint ->
            if (paint === p) paint.shader = LinearGradient(0f, 0f, w, 0f, intArrayOf(c1, Palette.ACCENT, c2), null, Shader.TileMode.CLAMP)
            c.drawPath(path, paint)
        }
        p.shader = null
    }

    private fun brackets(c: Canvas, l: Float, tp: Float, r: Float, b: Float) {
        val len = (r - l) * 0.18f
        glowStroke(c1, context.dpf(3.5f)) { paint ->
            c.drawLine(l, tp, l + len, tp, paint); c.drawLine(l, tp, l, tp + len, paint)
            c.drawLine(r, tp, r - len, tp, paint); c.drawLine(r, tp, r, tp + len, paint)
            c.drawLine(l, b, l + len, b, paint); c.drawLine(l, b, l, b - len, paint)
            c.drawLine(r, b, r - len, b, paint); c.drawLine(r, b, r, b - len, paint)
        }
    }

    private fun scanLine(c: Canvas, l: Float, tp: Float, r: Float, b: Float, t: Float) {
        if (success) return
        val y = tp + (sin(t * 1.8f) * 0.5f + 0.5f) * (b - tp)
        val band = context.dpf(26f)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, y - band, 0f, y + band,
            intArrayOf(Color.TRANSPARENT, Palette.alpha(c1, 90), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        c.drawRect(l, y - band, r, y + band, p)
        p.shader = null
        glowStroke(c1, context.dpf(2f)) { paint -> c.drawLine(l, y, r, y, paint) }
    }

    private fun finger(c: Canvas, t: Float) {
        val s = min(width, height) * 0.92f
        val cx = width / 2f
        val cy = height / 2f
        val l = cx - s / 2
        val tp = cy - s / 2
        brackets(c, l, tp, l + s, tp + s)
        for (i in 0 until 9) {
            val r = s * 0.05f + i * s * 0.036f
            rect.set(cx - r * 0.78f, cy - r, cx + r * 0.78f, cy + r * 1.08f)
            val start = 190f + i * 9f + sin(t + i) * 4f
            val sweep = 250f - i * 9f
            val col = Palette.alpha(c1, 140 + i * 12)
            glowStroke(col, context.dpf(2.4f)) { paint ->
                c.drawArc(rect, start, sweep * 0.62f, false, paint)
                c.drawArc(rect, start + sweep * 0.7f, sweep * 0.3f, false, paint)
            }
        }
        scanLine(c, l + s * 0.12f, tp + s * 0.08f, l + s * 0.88f, tp + s * 0.92f, t)
    }

    private fun face(c: Canvas, t: Float) {
        val s = min(width, height) * 0.92f
        val cx = width / 2f
        val cy = height / 2f
        val l = cx - s / 2
        val tp = cy - s / 2
        brackets(c, l, tp, l + s, tp + s)
        val rx = s * 0.27f
        val ry = s * 0.38f
        val col = Palette.alpha(c1, 170)
        // rotating meridians
        for (k in 0 until 6) {
            val f = abs(cos(t * 0.9f + k * PI.toFloat() / 6f))
            rect.set(cx - rx * f, cy - ry, cx + rx * f, cy + ry)
            glowStroke(Palette.alpha(col, (90 + 110 * f).toInt()), context.dpf(1.4f)) { paint -> c.drawOval(rect, paint) }
        }
        // latitudes
        for (j in -4..4) {
            val fy = j / 5f
            val yy = cy + fy * ry
            val half = rx * sqrt(max(0f, 1f - fy * fy))
            path.reset()
            path.moveTo(cx - half, yy)
            path.quadTo(cx, yy + ry * 0.07f, cx + half, yy)
            glowStroke(Palette.alpha(col, 150), context.dpf(1.2f)) { paint -> c.drawPath(path, paint) }
        }
        rect.set(cx - rx, cy - ry, cx + rx, cy + ry)
        glowStroke(c1, context.dpf(2.6f)) { paint -> c.drawOval(rect, paint) }
        // eyes
        p.style = Paint.Style.FILL
        p.color = Palette.alpha(c1, 200)
        c.drawOval(RectF(cx - rx * 0.55f, cy - ry * 0.18f, cx - rx * 0.2f, cy - ry * 0.06f), p)
        c.drawOval(RectF(cx + rx * 0.2f, cy - ry * 0.18f, cx + rx * 0.55f, cy - ry * 0.06f), p)
        scanLine(c, cx - rx * 1.25f, cy - ry, cx + rx * 1.25f, cy + ry, t)
    }

    private fun radar(c: Canvas, t: Float) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) * 0.46f
        for (k in 1..4) glowStroke(Palette.alpha(c1, 70 + k * 20), context.dpf(1.2f)) { paint -> c.drawCircle(cx, cy, r * k / 4f, paint) }
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f
        p.color = Palette.alpha(c1, 70)
        c.drawLine(cx - r, cy, cx + r, cy, p)
        c.drawLine(cx, cy - r, cx, cy + r, p)
        val ang = (t * 140f) % 360f
        val sweep = SweepGradient(cx, cy, intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Palette.alpha(c1, 170)), floatArrayOf(0f, 0.72f, 1f))
        matrix.reset()
        matrix.postRotate(ang, cx, cy)
        sweep.setLocalMatrix(matrix)
        p.style = Paint.Style.FILL
        p.shader = sweep
        c.drawCircle(cx, cy, r, p)
        p.shader = null
        if (t - lastBlip > 0.6f) {
            lastBlip = t
            val a = rnd.nextFloat() * 2 * PI.toFloat()
            val d = (0.2f + rnd.nextFloat() * 0.75f) * r
            blips += floatArrayOf(cx + cos(a) * d, cy + sin(a) * d, t)
        }
        val iter = blips.iterator()
        while (iter.hasNext()) {
            val bl = iter.next()
            val age = t - bl[2]
            if (age > 2.5f) {
                iter.remove()
                continue
            }
            val alpha = ((1f - age / 2.5f) * 255).toInt()
            p.color = Palette.alpha(c2, alpha / 3)
            c.drawCircle(bl[0], bl[1], context.dpf(9f), p)
            p.color = Palette.alpha(c1, alpha)
            c.drawCircle(bl[0], bl[1], context.dpf(3.5f), p)
        }
        p.color = c1
        c.drawCircle(cx, cy, context.dpf(4f), p)
    }

    private fun orbit(c: Canvas, t: Float) {
        val cx = width / 2f
        val cy = height / 2f
        val s = min(width, height).toFloat()
        // phone
        val pw = s * 0.2f
        val ph = s * 0.38f
        rect.set(cx - pw / 2, cy - ph / 2, cx + pw / 2, cy + ph / 2)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, intArrayOf(c2, Palette.ACCENT, c1), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, pw * 0.18f, pw * 0.18f, p)
        p.shader = null
        glowStroke(Palette.alpha(Color.WHITE, 120), context.dpf(1.5f)) { paint -> c.drawRoundRect(rect, pw * 0.18f, pw * 0.18f, paint) }
        val rx = s * 0.46f
        val ry = s * 0.13f
        for (k in 0 until 3) {
            c.save()
            c.rotate(-25f + k * 60f + sin(t * 0.3f) * 6f, cx, cy)
            rect.set(cx - rx, cy - ry, cx + rx, cy + ry)
            glowStroke(Palette.alpha(c1, 150), context.dpf(1.4f)) { paint -> c.drawOval(rect, paint) }
            val a = t * (1.1f + k * 0.4f) + k * 2f
            p.style = Paint.Style.FILL
            p.color = Palette.alpha(c2, 90)
            c.drawCircle(cx + cos(a) * rx, cy + sin(a) * ry, context.dpf(8f), p)
            p.color = c1
            c.drawCircle(cx + cos(a) * rx, cy + sin(a) * ry, context.dpf(4f), p)
            c.restore()
        }
    }

    private fun batteryArt(c: Canvas, t: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        val bw = min(w * 0.62f, h * 1.6f)
        val bh = bw * 0.46f
        val l = (w - bw) / 2
        val tp = (h - bh) / 2
        val r = bh * 0.18f
        rect.set(l, tp, l + bw, tp + bh)
        val pct = if (battery < 0) 0.6f else battery / 100f
        val col = when {
            battery in 0..19 -> Palette.FAIL
            battery in 20..39 -> Palette.SKIP
            else -> Palette.PASS
        }
        val inset = bh * 0.09f
        val fill = RectF(l + inset, tp + inset, l + inset + (bw - 2 * inset) * pct, tp + bh - inset)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(fill.left, 0f, fill.right, 0f, intArrayOf(Palette.alpha(col, 200), col, Color.rgb(134, 239, 172)), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(fill, r * 0.6f, r * 0.6f, p)
        // sheen
        val sx = l + ((t * 0.5f) % 1.4f - 0.2f) * bw
        p.shader = LinearGradient(sx - bw * 0.15f, 0f, sx + bw * 0.15f, 0f,
            intArrayOf(Color.TRANSPARENT, Color.argb(90, 255, 255, 255), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        c.save()
        c.clipRect(fill)
        c.drawRect(fill, p)
        c.restore()
        p.shader = null
        glowStroke(col, context.dpf(3f)) { paint -> c.drawRoundRect(rect, r, r, paint) }
        p.style = Paint.Style.FILL
        p.color = col
        c.drawRoundRect(RectF(l + bw + context.dpf(3f), tp + bh * 0.3f, l + bw + context.dpf(11f), tp + bh * 0.7f), r * 0.4f, r * 0.4f, p)
        txt.textSize = bh * 0.5f
        txt.color = Color.WHITE
        txt.setShadowLayer(8f, 0f, 0f, Color.argb(160, 0, 0, 0))
        c.drawText(if (battery >= 0) "$battery%" else "…", w / 2, tp + bh / 2 + txt.textSize * 0.35f, txt)
    }

    private fun pulse(c: Canvas, t: Float) {
        val cx = width / 2f
        val cy = height / 2f
        val s = min(width, height).toFloat()
        for (k in 0 until 4) {
            val ph = (t * 0.7f + k / 4f) % 1f
            val r = s * 0.1f + ph * s * 0.38f
            glowStroke(Palette.alpha(c1, ((1f - ph) * 220).toInt()), context.dpf(2f)) { paint -> c.drawCircle(cx, cy, r, paint) }
        }
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy, s * 0.14f, intArrayOf(c1, Palette.alpha(c2, 160), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, s * 0.14f * (1f + 0.12f * sin(t * 4f)), p)
        p.shader = null
    }
}

/** Thin animated "scanning" bar; turns solid green / red when finished. */
class ScanBar(ctx: Context) : View(ctx) {
    private var state = 0 // 0 scanning, 1 pass, 2 fail
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    fun pass() {
        state = 1
        invalidate()
    }

    fun fail() {
        state = 2
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2
        rect.set(0f, 0f, w, h)
        p.shader = null
        p.color = Palette.alpha(Palette.ACCENT, if (Palette.dark) 50 else 30)
        canvas.drawRoundRect(rect, r, r, p)
        when (state) {
            1, 2 -> {
                p.color = if (state == 1) Palette.PASS else Palette.FAIL
                canvas.drawRoundRect(rect, r, r, p)
            }
            else -> {
                val t = now()
                val x = ((t * 0.55f) % 1.4f - 0.2f) * w
                val segW = w * 0.38f
                p.shader = LinearGradient(x - segW / 2, 0f, x + segW / 2, 0f,
                    intArrayOf(Color.TRANSPARENT, Palette.CYAN, Palette.PURPLE, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
                rect.set(max(0f, x - segW / 2), 0f, min(w, x + segW / 2), h)
                canvas.drawRoundRect(rect, r, r, p)
                postInvalidateOnAnimation()
            }
        }
    }
}

/** Slowly animated backdrop: gradient, faint grid and drifting light glows. */
class HoloBackground(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val t = now()
        p.shader = LinearGradient(0f, 0f, w, h,
            if (Palette.dark) intArrayOf(Color.rgb(6, 9, 24), Color.rgb(9, 14, 42), Color.rgb(17, 10, 44))
            else intArrayOf(Color.rgb(246, 248, 253), Color.rgb(238, 243, 255), Color.rgb(245, 240, 255)),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p)
        p.shader = null
        // grid
        val step = context.dpf(34f)
        p.strokeWidth = 1f
        p.color = Palette.alpha(Palette.ACCENT, if (Palette.dark) 14 else 12)
        val off = (t * 8f) % step
        var x = 0f
        while (x < w) {
            canvas.drawLine(x, 0f, x, h, p)
            x += step
        }
        var y = off
        while (y < h) {
            canvas.drawLine(0f, y, w, y, p)
            y += step
        }
        // drifting glows
        val gx1 = w * (0.2f + 0.15f * sin(t * 0.25f))
        val gy1 = h * (0.25f + 0.1f * cos(t * 0.2f))
        p.shader = RadialGradient(gx1, gy1, w * 0.7f, Palette.alpha(Palette.ACCENT, if (Palette.dark) 55 else 30), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p)
        val gx2 = w * (0.85f + 0.1f * cos(t * 0.18f))
        val gy2 = h * (0.8f + 0.08f * sin(t * 0.22f))
        p.shader = RadialGradient(gx2, gy2, w * 0.75f, Palette.alpha(Palette.PURPLE, if (Palette.dark) 60 else 28), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p)
        p.shader = null
        postInvalidateDelayed(40)
    }
}

/** Heartbeat-line logo mark. */
class PulseLogo(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val pts = floatArrayOf(0f, 0.6f, 0.22f, 0.6f, 0.32f, 0.3f, 0.45f, 0.95f, 0.58f, 0.1f, 0.7f, 0.6f, 1f, 0.6f)
        path.reset()
        var i = 0
        while (i < pts.size) {
            val x = pts[i] * w
            val y = pts[i + 1] * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            i += 2
        }
        p.shader = null
        p.strokeWidth = context.dpf(7f)
        p.color = Palette.alpha(Palette.CYAN, 50)
        canvas.drawPath(path, p)
        p.strokeWidth = context.dpf(2.6f)
        p.shader = LinearGradient(0f, 0f, w, 0f, intArrayOf(Palette.CYAN, Palette.ACCENT, Palette.PURPLE), null, Shader.TileMode.CLAMP)
        canvas.drawPath(path, p)
    }
}

/** Live diagnostic checklist: "✓ OK" / "✕ FAIL" / "…" rows. */
class Checklist(ctx: Context) : LinearLayout(ctx) {
    companion object {
        const val PENDING = 0
        const val OK = 1
        const val FAIL = 2
        const val RUNNING = 3
    }

    private val rows = LinkedHashMap<String, Pair<TextView, TextView>>()

    init {
        orientation = VERTICAL
        background = roundedBg(Palette.alpha(Palette.CARD, if (Palette.dark) 220 else 245), context.dpf(18f), Palette.alpha(Palette.ACCENT, 90), context.dp(1))
        setPadding(context.dp(14), context.dp(6), context.dp(14), context.dp(6))
    }

    fun add(vararg names: String): Checklist {
        for (n in names) {
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, context.dp(7), 0, context.dp(7))
            }
            val icon = context.label("", 12f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
            val name = context.label(n, 14f, Palette.TEXT)
            val st = context.label("", 13f, Palette.MUTED, true)
            row.addView(icon, LayoutParams(context.dp(22), context.dp(22)).apply { marginEnd = context.dp(12) })
            row.addView(name, LayoutParams(0, WRAP, 1f))
            row.addView(st)
            addView(row)
            rows[n] = icon to st
            set(n, PENDING)
        }
        return this
    }

    fun set(name: String, state: Int, text: String? = null) {
        val (icon, st) = rows[name] ?: return
        when (state) {
            OK -> {
                icon.text = "✓"
                icon.background = roundedBg(Palette.PASS, context.dpf(11f))
                st.setTextColor(Palette.PASS)
                st.text = text ?: "✓ OK"
            }
            FAIL -> {
                icon.text = "✕"
                icon.background = roundedBg(Palette.FAIL, context.dpf(11f))
                st.setTextColor(Palette.FAIL)
                st.text = text ?: "✕ FAIL"
            }
            RUNNING -> {
                icon.text = "•"
                icon.background = roundedBg(Palette.ACCENT, context.dpf(11f))
                st.setTextColor(Palette.ACCENT)
                st.text = text ?: "Testing…"
            }
            else -> {
                icon.text = ""
                icon.background = roundedBg(Color.TRANSPARENT, context.dpf(11f), Palette.MUTED, context.dp(2))
                st.setTextColor(Palette.MUTED)
                st.text = text ?: "Pending"
            }
        }
    }
}
