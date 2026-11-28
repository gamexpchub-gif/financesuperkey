package com.gamex.devicetester

import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.gamex.devicetester.core.DeviceInfo
import com.gamex.devicetester.core.ResultStore
import com.gamex.devicetester.core.TestRegistry
import com.gamex.devicetester.core.TestStatus
import com.gamex.devicetester.ui.MATCH
import com.gamex.devicetester.ui.Palette
import com.gamex.devicetester.ui.WRAP
import com.gamex.devicetester.ui.actionButton
import com.gamex.devicetester.ui.card
import com.gamex.devicetester.ui.dp
import com.gamex.devicetester.ui.label
import com.gamex.devicetester.ui.lpMatchWrap
import com.gamex.devicetester.ui.statusChip
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Final diagnostic report with score/grade, per-test notes, and text / PDF export. */
class ReportActivity : AppCompatActivity() {

    private data class Row(val category: String, val title: String, val status: TestStatus, val note: String?)

    private lateinit var rows: List<Row>
    private var pass = 0
    private var fail = 0
    private var skip = 0
    private var untested = 0
    private val date: String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rows = TestRegistry.build(this).map {
            Row(it.category, it.title, ResultStore.status(this, it.id), ResultStore.note(this, it.id))
        }
        pass = rows.count { it.status == TestStatus.PASS }
        fail = rows.count { it.status == TestStatus.FAIL }
        skip = rows.count { it.status == TestStatus.SKIP }
        untested = rows.count { it.status == TestStatus.UNTESTED }
        buildUi()
    }

    private fun score(): Int = if (pass + fail > 0) pass * 100 / (pass + fail) else 0

    private fun grade(): String {
        if (pass + fail == 0) return "Not tested"
        val s = score()
        return when {
            fail == 0 && untested == 0 -> "A+  Excellent — all tested hardware working"
            s >= 95 -> "A  Very good"
            s >= 85 -> "B  Good — minor issues"
            s >= 70 -> "C  Fair — needs repair"
            else -> "D  Poor — major faults"
        }
    }

    private fun buildUi() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(24))
        }
        val scroll = ScrollView(this).apply { setBackgroundColor(Palette.BG) }
        scroll.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
        setContentView(scroll)

        col.addView(label("Diagnostic report", 24f, Palette.TEXT, true))
        col.addView(label("${Build.MANUFACTURER} ${Build.MODEL} • $date", 13f, Palette.MUTED), lpMatchWrap(bottom = dp(12)))

        val sc = card()
        val scoreColor = when {
            pass + fail == 0 -> Palette.MUTED
            score() >= 90 -> Palette.PASS
            score() >= 70 -> Palette.SKIP
            else -> Palette.FAIL
        }
        sc.addView(label("${score()}%", 52f, scoreColor, true).apply { gravity = Gravity.CENTER }, lpMatchWrap())
        sc.addView(label(grade(), 16f, Palette.TEXT, true).apply { gravity = Gravity.CENTER }, lpMatchWrap())
        sc.addView(label("✓ $pass passed   ✕ $fail failed   ⏭ $skip skipped   • $untested not tested", 13f, Palette.MUTED)
            .apply { gravity = Gravity.CENTER }, lpMatchWrap(top = dp(6)))
        col.addView(sc, lpMatchWrap(bottom = dp(12)))

        val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btns.addView(actionButton("📄 Export PDF") { exportPdf() }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(6) })
        btns.addView(actionButton("↗ Share text", Palette.CARD_ALT) { shareText() }, LinearLayout.LayoutParams(0, WRAP, 1f))
        col.addView(btns, lpMatchWrap(bottom = dp(8)))

        val failed = rows.filter { it.status == TestStatus.FAIL }
        if (failed.isNotEmpty()) {
            val fc = card()
            fc.addView(label("Faults found", 15f, Palette.FAIL, true))
            for (r in failed) fc.addView(label("• ${r.title}" + (r.note?.let { " — $it" } ?: ""), 13f, Palette.TEXT), lpMatchWrap(top = dp(4)))
            col.addView(fc, lpMatchWrap(bottom = dp(8)))
        }

        for ((cat, group) in rows.groupBy { it.category }) {
            col.addView(label(cat.uppercase(), 12f, Palette.ACCENT, true), lpMatchWrap(top = dp(12), bottom = dp(6)))
            val box = card()
            for (r in group) {
                val line = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(6), 0, dp(6))
                }
                val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                texts.addView(label(r.title, 14f, Palette.TEXT))
                if (!r.note.isNullOrBlank()) texts.addView(label(r.note, 11f, Palette.MUTED))
                line.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
                line.addView(statusChip(r.status))
                box.addView(line)
            }
            col.addView(box, lpMatchWrap())
        }
    }

    private fun reportLines(): List<Pair<String, Int>> {
        // Pair(text, style) — style 0 normal, 1 heading, 2 title, 3 pass, 4 fail, 5 skip
        val out = mutableListOf<Pair<String, Int>>()
        out += "GX Device Tester — Diagnostic Report" to 2
        out += "${Build.MANUFACTURER} ${Build.MODEL}   •   $date" to 0
        out += "Score: ${score()}%   Grade: ${grade()}" to 1
        out += "Passed $pass   Failed $fail   Skipped $skip   Not tested $untested" to 0
        out += "" to 0
        for ((cat, group) in rows.groupBy { it.category }) {
            out += cat to 1
            for (r in group) {
                val st = when (r.status) {
                    TestStatus.PASS -> "PASS"
                    TestStatus.FAIL -> "FAIL"
                    TestStatus.SKIP -> "SKIP"
                    TestStatus.UNTESTED -> "—"
                }
                val style = when (r.status) {
                    TestStatus.PASS -> 3
                    TestStatus.FAIL -> 4
                    TestStatus.SKIP -> 5
                    TestStatus.UNTESTED -> 0
                }
                out += "[$st]  ${r.title}" + (r.note?.takeIf { it.isNotBlank() }?.let { "  ($it)" } ?: "") to style
            }
            out += "" to 0
        }
        for ((section, list) in DeviceInfo.collect(this)) {
            out += section to 1
            for ((k, v) in list) out += "$k: ${v.replace('\n', ' | ')}" to 0
            out += "" to 0
        }
        return out
    }

    private fun shareText() {
        val text = reportLines().joinToString("\n") { it.first }
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Device report — ${Build.MODEL}")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(i, "Share report"))
    }

    private fun exportPdf() {
        try {
            val doc = PdfDocument()
            val pageW = 595
            val pageH = 842
            val margin = 40f
            val maxW = pageW - 2 * margin
            var pageNo = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            var y = margin
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            for ((text, style) in reportLines()) {
                paint.typeface = if (style in 1..2) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                paint.textSize = when (style) {
                    2 -> 18f
                    1 -> 12.5f
                    else -> 9.5f
                }
                paint.color = when (style) {
                    1 -> Color.rgb(30, 64, 175)
                    3 -> Color.rgb(21, 128, 61)
                    4 -> Color.rgb(185, 28, 28)
                    5 -> Color.rgb(180, 83, 9)
                    else -> Color.rgb(17, 24, 39)
                }
                var remaining = text
                do {
                    val n = if (remaining.isEmpty()) 0 else paint.breakText(remaining, true, maxW, null).coerceAtLeast(1)
                    val chunk = remaining.take(n)
                    remaining = remaining.drop(n)
                    val lineH = paint.textSize * 1.45f
                    if (y + lineH > pageH - margin) {
                        doc.finishPage(page)
                        pageNo++
                        page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
                        y = margin
                    }
                    y += lineH
                    page.canvas.drawText(chunk, margin, y, paint)
                } while (remaining.isNotEmpty())
            }
            doc.finishPage(page)

            val dir = File(cacheDir, "reports").apply { mkdirs() }
            val safeModel = Build.MODEL.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            val file = File(dir, "DeviceReport_${safeModel}_$stamp.pdf")
            FileOutputStream(file).use { doc.writeTo(it) }
            doc.close()

            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(i, "Share / save PDF report"))
        } catch (e: Exception) {
            Toast.makeText(this, "PDF export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
