package com.gamex.devicetester.tests

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.gamex.devicetester.core.BaseTestActivity
import com.gamex.devicetester.core.TestStatus
import com.gamex.devicetester.ui.MATCH
import com.gamex.devicetester.ui.Palette
import com.gamex.devicetester.ui.WRAP
import com.gamex.devicetester.ui.dp
import com.gamex.devicetester.ui.label
import com.gamex.devicetester.ui.roundedBg
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.max
import kotlin.math.roundToInt

/** Full-screen solid colours + gradient to find dead/stuck pixels, lines, burn-in and banding. */
class DisplayTestActivity : BaseTestActivity() {
    override val fullscreen: Boolean get() = true

    private val screens = listOf(
        Color.RED to "Red", Color.GREEN to "Green", Color.BLUE to "Blue",
        Color.WHITE to "White", Color.BLACK to "Black", Color.GRAY to "Gray",
        0 to "Gradient (banding check)",
    )
    private var index = 0
    private lateinit var surface: View
    private lateinit var hint: TextView

    override fun onCreateTest(savedInstanceState: Bundle?) {
        surface = View(this)
        content.addView(surface, FrameLayout.LayoutParams(MATCH, MATCH))
        hint = label("", 16f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = roundedBg(Color.argb(170, 0, 0, 0), dp(12).toFloat())
        }
        content.addView(hint, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER).apply {
            leftMargin = dp(24)
            rightMargin = dp(24)
        })
        surface.setOnClickListener {
            index++
            if (index < screens.size) show() else ask()
        }
        show()
    }

    private fun show() {
        val (color, name) = screens[index]
        if (color == 0) {
            surface.background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.BLACK, Color.WHITE),
            )
        } else {
            surface.background = null
            surface.setBackgroundColor(color)
        }
        hint.text = "$name  (${index + 1}/${screens.size})\nLook for dead / stuck pixels, lines, spots, yellow tint or burn-in.\nTap anywhere for next colour."
        hint.alpha = 1f
        hint.animate().alpha(0f).setStartDelay(1800).setDuration(400).start()
    }

    private fun ask() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Display check")
            .setMessage("Did you see any dead pixels, lines, burn-in, spots, uneven backlight or discoloration?")
            .setPositiveButton("No — display OK") { _, _ -> finishWith(TestStatus.PASS, "No visible defects") }
            .setNegativeButton("Yes — defect") { _, _ -> finishWith(TestStatus.FAIL, "Display defect reported") }
            .setNeutralButton("Repeat") { _, _ ->
                index = 0
                show()
            }
            .setCancelable(false)
            .show()
    }
}

/** Every cell of a grid must be touched — detects dead touch zones (incl. edges). */
class TouchTestActivity : BaseTestActivity() {
    override val fullscreen: Boolean get() = true

    private lateinit var progress: TextView

    override fun onCreateTest(savedInstanceState: Bundle?) {
        val grid = TouchGridView(this, onProgress = { doneCells, total ->
            progress.text = "Draw over every square\n$doneCells / $total"
        }, onComplete = {
            progress.text = "✓ All touch zones OK"
            progress.setTextColor(Palette.PASS)
            autoPass("All touch cells responded")
        })
        content.addView(grid, FrameLayout.LayoutParams(MATCH, MATCH))
        progress = label("Draw over every square\n(back = fail / skip)", 16f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
        }
        content.addView(progress, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
    }
}

class TouchGridView(
    ctx: Context,
    private val onProgress: (Int, Int) -> Unit,
    private val onComplete: () -> Unit,
) : View(ctx) {
    private val cols = 7
    private var rows = 12
    private var cells = BooleanArray(0)
    private var count = 0
    private var completed = false
    private val fill = Paint().apply { color = Palette.PASS }
    private val line = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.argb(110, 255, 255, 255)
        strokeWidth = 2f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0) return
        rows = max(8, (cols * h.toFloat() / w).roundToInt())
        cells = BooleanArray(rows * cols)
        count = 0
        onProgress(0, cells.size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cells.isEmpty()) return
        val cw = width.toFloat() / cols
        val ch = height.toFloat() / rows
        for (r in 0 until rows) for (c in 0 until cols) {
            val l = c * cw
            val t = r * ch
            if (cells[r * cols + c]) canvas.drawRect(l, t, l + cw, t + ch, fill)
            canvas.drawRect(l, t, l + cw, t + ch, line)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        for (p in 0 until e.pointerCount) {
            for (h in 0 until e.historySize) mark(e.getHistoricalX(p, h), e.getHistoricalY(p, h))
            mark(e.getX(p), e.getY(p))
        }
        invalidate()
        return true
    }

    private fun mark(x: Float, y: Float) {
        if (cells.isEmpty() || completed) return
        val c = (x / (width.toFloat() / cols)).toInt().coerceIn(0, cols - 1)
        val r = (y / (height.toFloat() / rows)).toInt().coerceIn(0, rows - 1)
        val idx = r * cols + c
        if (!cells[idx]) {
            cells[idx] = true
            count++
            onProgress(count, cells.size)
            if (count == cells.size) {
                completed = true
                onComplete()
            }
        }
    }
}

/** Shows every finger as a coloured circle and reports the max simultaneous touches. */
class MultiTouchTestActivity : BaseTestActivity() {
    private var maxPointers = 0

    override fun currentNote(): String = "Max simultaneous touches: $maxPointers"

    override fun onCreateTest(savedInstanceState: Bundle?) {
        setInstruction("Place 2 to 5 fingers on the box at the same time and move them. Passes automatically at 3+ fingers.")
        val view = MultiTouchView(this) { n ->
            if (n > maxPointers) maxPointers = n
            setStatus("Fingers now: $n   •   Max: $maxPointers", if (maxPointers >= 2) Palette.PASS else Palette.TEXT)
            if (maxPointers >= 3) autoPass()
        }
        view.background = roundedBg(Palette.CARD, dp(16).toFloat(), Palette.STROKE, dp(1))
        content.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
    }
}

class MultiTouchView(ctx: Context, private val onCount: (Int) -> Unit) : View(ctx) {
    private val colors = intArrayOf(
        Color.parseColor("#3B82F6"), Color.parseColor("#22C55E"), Color.parseColor("#F59E0B"),
        Color.parseColor("#EF4444"), Color.parseColor("#A855F7"), Color.parseColor("#06B6D4"),
        Color.parseColor("#EC4899"), Color.parseColor("#84CC16"), Color.parseColor("#F97316"),
        Color.parseColor("#E5E7EB"),
    )
    private val points = HashMap<Int, Pair<Float, Float>>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 64f
        textAlign = Paint.Align.CENTER
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        points.clear()
        val up = e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL
        if (!up) {
            for (i in 0 until e.pointerCount) {
                if (e.actionMasked == MotionEvent.ACTION_POINTER_UP && i == e.actionIndex) continue
                points[e.getPointerId(i)] = e.getX(i) to e.getY(i)
            }
        }
        onCount(points.size)
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for ((id, p) in points) {
            paint.color = colors[id % colors.size]
            canvas.drawCircle(p.first, p.second, 90f, paint)
            canvas.drawText((id + 1).toString(), p.first, p.second - 110f, text)
        }
        if (points.isEmpty()) {
            text.alpha = 90
            canvas.drawText("Touch here", width / 2f, height / 2f, text)
            text.alpha = 255
        }
    }
}
