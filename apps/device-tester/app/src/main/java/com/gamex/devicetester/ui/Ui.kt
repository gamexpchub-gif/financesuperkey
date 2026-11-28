package com.gamex.devicetester.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.gamex.devicetester.core.TestStatus
import com.google.android.material.button.MaterialButton
import kotlin.math.roundToInt

object Palette {
    val BG = Color.parseColor("#0B1220")
    val CARD = Color.parseColor("#121C30")
    val CARD_ALT = Color.parseColor("#22314F")
    val STROKE = Color.parseColor("#24334F")
    val ACCENT = Color.parseColor("#3B82F6")
    val PASS = Color.parseColor("#22C55E")
    val FAIL = Color.parseColor("#EF4444")
    val SKIP = Color.parseColor("#F59E0B")
    val TEXT = Color.parseColor("#E5E7EB")
    val MUTED = Color.parseColor("#94A3B8")
}

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun Context.label(
    text: CharSequence = "",
    sizeSp: Float = 14f,
    color: Int = Palette.TEXT,
    bold: Boolean = false,
): TextView = TextView(this).apply {
    this.text = text
    textSize = sizeSp
    setTextColor(color)
    if (bold) setTypeface(typeface, Typeface.BOLD)
}

fun roundedBg(color: Int, radiusPx: Float, strokeColor: Int? = null, strokePx: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusPx
        setColor(color)
        if (strokeColor != null) setStroke(strokePx, strokeColor)
    }

fun Context.card(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = roundedBg(Palette.CARD, dpf(16f), Palette.STROKE, dp(1))
    setPadding(dp(16), dp(14), dp(16), dp(14))
}

fun Context.actionButton(
    text: String,
    color: Int = Palette.ACCENT,
    onClick: (View) -> Unit,
): MaterialButton = MaterialButton(this).apply {
    this.text = text
    isAllCaps = false
    backgroundTintList = ColorStateList.valueOf(color)
    setTextColor(Color.WHITE)
    cornerRadius = dp(12)
    textSize = 15f
    insetTop = 0
    insetBottom = 0
    minHeight = dp(48)
    setOnClickListener(onClick)
}

fun lpMatchWrap(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(MATCH, WRAP).apply {
        topMargin = top
        bottomMargin = bottom
    }

fun statusColor(s: TestStatus): Int = when (s) {
    TestStatus.PASS -> Palette.PASS
    TestStatus.FAIL -> Palette.FAIL
    TestStatus.SKIP -> Palette.SKIP
    TestStatus.UNTESTED -> Palette.MUTED
}

fun statusLabel(s: TestStatus): String = when (s) {
    TestStatus.PASS -> "✓ PASS"
    TestStatus.FAIL -> "✕ FAIL"
    TestStatus.SKIP -> "⏭ SKIP"
    TestStatus.UNTESTED -> "• —"
}

/** A chip-like status label. */
fun Context.statusChip(s: TestStatus): TextView = label(statusLabel(s), 12f, Color.WHITE, true).apply {
    gravity = Gravity.CENTER
    setPadding(dp(10), dp(4), dp(10), dp(4))
    background = roundedBg(statusColor(s), dpf(10f))
}

/** Updatable two-column key/value list. */
class KeyValueList(ctx: Context) : LinearLayout(ctx) {
    private val rows = LinkedHashMap<String, TextView>()

    init {
        orientation = VERTICAL
    }

    fun header(title: String) {
        addView(context.label(title, 15f, Palette.ACCENT, true), lpMatchWrap(top = context.dp(12), bottom = context.dp(4)))
    }

    fun set(key: String, value: String, color: Int = Palette.TEXT) {
        val tv = rows[key] ?: run {
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                setPadding(0, context.dp(5), 0, context.dp(5))
            }
            val k = context.label(key, 13f, Palette.MUTED)
            val v = context.label("", 13f, Palette.TEXT, true).apply { gravity = Gravity.END }
            row.addView(k, LayoutParams(0, WRAP, 1f))
            row.addView(v, LayoutParams(0, WRAP, 1.4f))
            addView(row, lpMatchWrap())
            rows[key] = v
            v
        }
        tv.text = value
        tv.setTextColor(color)
    }
}

fun formatBytes(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1.0) String.format("%.2f GB", gb) else String.format("%.0f MB", bytes / (1024.0 * 1024.0))
}
