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

/** Theme-aware colours. [dark] is set by ThemedActivity before any view is built. */
object Palette {
    @JvmStatic
    var dark = true

    private fun pick(d: Long, l: Long): Int = (if (dark) d else l).toInt()

    val BG get() = pick(0xFF070B1A, 0xFFF3F5FB)
    val CARD get() = pick(0xFF10172F, 0xFFFFFFFF)
    val CARD_ALT get() = pick(0xFF1B2446, 0xFFEEF1F8)
    val STROKE get() = pick(0xFF232E57, 0xFFE3E8F2)
    val TEXT get() = pick(0xFFF1F5F9, 0xFF0F172A)
    val MUTED get() = pick(0xFF94A3B8, 0xFF64748B)
    val ACCENT get() = pick(0xFF3B82F6, 0xFF2563EB)
    val PURPLE get() = pick(0xFF8B5CF6, 0xFF7C3AED)
    val CYAN get() = pick(0xFF22D3EE, 0xFF0891B2)
    val PASS get() = pick(0xFF22C55E, 0xFF16A34A)
    val FAIL get() = pick(0xFFEF4444, 0xFFDC2626)
    val SKIP get() = pick(0xFFF59E0B, 0xFFD97706)

    /** [color] with the given alpha (0–255). */
    fun alpha(color: Int, a: Int): Int = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
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
    setTextColor(if (color == Palette.CARD_ALT || color == Palette.CARD) Palette.TEXT else Color.WHITE)
    cornerRadius = dp(16)
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
