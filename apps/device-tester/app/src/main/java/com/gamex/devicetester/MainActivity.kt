package com.gamex.devicetester

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.gamex.devicetester.core.DeviceInfo
import com.gamex.devicetester.core.QuickTile
import com.gamex.devicetester.core.QuickTiles
import com.gamex.devicetester.core.ResultStore
import com.gamex.devicetester.core.TestItem
import com.gamex.devicetester.core.TestRegistry
import com.gamex.devicetester.core.TestStatus
import com.gamex.devicetester.ui.MATCH
import com.gamex.devicetester.ui.Palette
import com.gamex.devicetester.ui.HoloBackground
import com.gamex.devicetester.ui.PhoneArtView
import com.gamex.devicetester.ui.PulseLogo
import com.gamex.devicetester.ui.ThemeManager
import com.gamex.devicetester.ui.ThemedActivity
import com.gamex.devicetester.ui.WRAP
import com.gamex.devicetester.ui.brandTitle
import com.gamex.devicetester.ui.circleButton
import com.gamex.devicetester.ui.dp
import com.gamex.devicetester.ui.dpf
import com.gamex.devicetester.ui.glassCard
import com.gamex.devicetester.ui.gradientBg
import com.gamex.devicetester.ui.iconBadge
import com.gamex.devicetester.ui.label
import com.gamex.devicetester.ui.lpMatchWrap
import com.gamex.devicetester.ui.pill
import com.gamex.devicetester.ui.roundedBg
import com.gamex.devicetester.ui.sectionHeader
import com.gamex.devicetester.ui.shieldIcon
import com.gamex.devicetester.ui.statusColor
import com.gamex.devicetester.ui.statusLabel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Home dashboard with bottom navigation: Home • History • Reports • More. */
class MainActivity : ThemedActivity() {

    private lateinit var items: List<TestItem>
    private lateinit var pages: FrameLayout
    private val navItems = mutableListOf<Pair<TextView, TextView>>()
    private var tab = 0

    private val singleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { showTab(tab) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        items = TestRegistry.build(this)
        tab = savedInstanceState?.getInt("tab", 0) ?: 0

        val stack = FrameLayout(this)
        stack.addView(HoloBackground(this), FrameLayout.LayoutParams(MATCH, MATCH))
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        stack.addView(root, FrameLayout.LayoutParams(MATCH, MATCH))
        pages = FrameLayout(this)
        root.addView(pages, LinearLayout.LayoutParams(MATCH, 0, 1f))
        root.addView(buildNav(), lpMatchWrap())
        setContentView(stack)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", tab)
    }

    override fun onResume() {
        super.onResume()
        showTab(tab)
    }

    // ------------------------------------------------------------ navigation

    private fun buildNav(): LinearLayout {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(10))
            background = roundedBg(Palette.CARD, dpf(0f), Palette.STROKE, dp(1))
            elevation = dpf(8f)
        }
        val defs = listOf("🏠" to "Home", "🕘" to "History", "📊" to "Reports", "⋯" to "More")
        defs.forEachIndexed { i, (icon, name) ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, dp(4))
                isClickable = true
                setOnClickListener {
                    if (i == 2) startActivity(Intent(this@MainActivity, ReportActivity::class.java)) else showTab(i)
                }
            }
            val ic = label(icon, 20f, Palette.MUTED).apply {
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(4), dp(14), dp(4))
            }
            val tx = label(name, 12f, Palette.MUTED, true).apply { gravity = Gravity.CENTER }
            item.addView(ic, LinearLayout.LayoutParams(WRAP, WRAP))
            item.addView(tx, LinearLayout.LayoutParams(WRAP, WRAP))
            nav.addView(item, LinearLayout.LayoutParams(0, WRAP, 1f))
            navItems += ic to tx
        }
        return nav
    }

    private fun showTab(t: Int) {
        tab = t
        navItems.forEachIndexed { i, (ic, tx) ->
            val on = i == t
            tx.setTextColor(if (on) Palette.ACCENT else Palette.MUTED)
            ic.background = if (on) roundedBg(Palette.alpha(Palette.ACCENT, if (Palette.dark) 50 else 30), dpf(14f)) else null
        }
        pages.removeAllViews()
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(24))
        }
        scroll.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
        pages.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))
        when (t) {
            1 -> buildHistory(col)
            3 -> buildMore(col)
            else -> buildHome(col)
        }
    }

    // ------------------------------------------------------------ home

    private fun buildHome(col: LinearLayout) {
        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(FrameLayout(this).apply {
            background = roundedBg(Palette.alpha(Palette.ACCENT, if (Palette.dark) 40 else 22), dpf(16f), Palette.alpha(Palette.CYAN, 120), dp(1))
            addView(PulseLogo(context), FrameLayout.LayoutParams(dp(36), dp(26), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(12) })
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(brandTitle(24f))
        titles.addView(label("Ultimate Device Health Checker", 12f, Palette.MUTED))
        header.addView(titles, LinearLayout.LayoutParams(0, WRAP, 1f))
        header.addView(circleButton(if (Palette.dark) "☀️" else "🌙", 44) { toggleTheme() }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(8)
        })
        header.addView(circleButton("⚙️", 44) { showTab(3) })
        col.addView(header, lpMatchWrap(bottom = dp(18)))

        col.addView(heroCard(), lpMatchWrap(bottom = dp(16)))
        col.addView(startButton(), lpMatchWrap(bottom = dp(22)))

        col.addView(sectionHeader("Quick Tests", "Tap to check") { showTab(1) }, lpMatchWrap(bottom = dp(12)))
        addGrid(col, QuickTiles.quick, 3, compact = false)

        col.addView(sectionHeader("Advanced Tools", "All tests") { showTab(1) }, lpMatchWrap(top = dp(14), bottom = dp(12)))
        addGrid(col, QuickTiles.advanced, 5, compact = true)

        // Banner
        val banner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = gradientBg(
                intArrayOf(Palette.alpha(Palette.PASS, if (Palette.dark) 35 else 22), Palette.alpha(Palette.PURPLE, if (Palette.dark) 55 else 28)),
                18f, strokeColor = Palette.alpha(Palette.PASS, 130),
            )
            isClickable = true
            setOnClickListener { startActivity(Intent(this@MainActivity, ReportActivity::class.java)) }
        }
        banner.addView(shieldIcon(Palette.PASS, 38), LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(12) })
        val bt = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bt.addView(label("Professional Grade Testing", 16f, Palette.PASS, true))
        bt.addView(label("Diagnose • Verify • PDF Report", 12f, Palette.MUTED))
        banner.addView(bt, LinearLayout.LayoutParams(0, WRAP, 1f))
        banner.addView(label("›", 26f, Palette.TEXT, true))
        col.addView(banner, lpMatchWrap(top = dp(16)))
    }

    private data class Summary(val pass: Int, val fail: Int, val skip: Int, val done: Int)

    private fun summary(): Summary {
        var p = 0
        var f = 0
        var s = 0
        for (it in items) {
            when (ResultStore.status(this, it.id)) {
                TestStatus.PASS -> p++
                TestStatus.FAIL -> f++
                TestStatus.SKIP -> s++
                TestStatus.UNTESTED -> Unit
            }
        }
        return Summary(p, f, s, p + f + s)
    }

    private fun heroCard(): View {
        val sum = summary()
        val (word, color) = when {
            sum.done == 0 -> "Ready to Test" to Palette.ACCENT
            sum.fail == 0 -> "Healthy!" to Palette.PASS
            else -> "Needs Attention" to Palette.FAIL
        }
        val frame = FrameLayout(this).apply {
            background = gradientBg(
                if (Palette.dark) intArrayOf(Color.rgb(13, 26, 66), Color.rgb(9, 20, 44), Palette.alpha(color, 70))
                else intArrayOf(Color.WHITE, Color.rgb(248, 250, 255), Palette.alpha(color, 40)),
                24f, strokeColor = Palette.alpha(color, 150),
            )
            elevation = if (Palette.dark) 0f else dpf(3f)
            isClickable = true
            setOnClickListener { startActivity(Intent(this@MainActivity, DeviceInfoActivity::class.java)) }
        }
        frame.addView(shieldIcon(Palette.alpha(color, 70), 96), FrameLayout.LayoutParams(dp(96), dp(96), Gravity.END or Gravity.CENTER_VERTICAL).apply {
            marginEnd = dp(10)
        })

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(18))
        }
        row.addView(PhoneArtView(this), LinearLayout.LayoutParams(dp(74), dp(128)).apply { marginEnd = dp(16) })
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val st = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        st.addView(label(if (sum.fail == 0) "✓" else "!", 20f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = roundedBg(color, dpf(18f))
        }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(10) })
        val stt = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        stt.addView(label("Your Device is", 13f, Palette.TEXT, true))
        stt.addView(label(word, 23f, color, true))
        st.addView(stt)
        info.addView(st, lpMatchWrap(bottom = dp(8)))

        val name = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        info.addView(label("📱  $name", 15f, Palette.TEXT, true), lpMatchWrap(bottom = dp(4)))
        info.addView(label("⚙️  Android ${Build.VERSION.RELEASE}", 13f, Palette.MUTED), lpMatchWrap(bottom = dp(3)))
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val storage = DeviceInfo.marketingStorage(StatFs(Environment.getDataDirectory().path).totalBytes)
        info.addView(label("💾  $storage GB • ${DeviceInfo.marketingRam(mem.totalMem)} GB RAM", 13f, Palette.MUTED), lpMatchWrap(bottom = dp(3)))
        val bRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val pct = batteryPct()
        bRow.addView(label("🔋  Battery: $pct%  ", 13f, Palette.MUTED))
        val batt = ResultStore.status(this, "battery")
        bRow.addView(pill(
            when (batt) {
                TestStatus.PASS -> "Good"
                TestStatus.FAIL -> "Check"
                else -> if (pct >= 20) "OK" else "Low"
            },
            if (batt == TestStatus.FAIL || pct < 20) Palette.FAIL else Palette.PASS, true,
        ).apply { textSize = 10f; setPadding(dp(8), dp(2), dp(8), dp(2)) })
        info.addView(bRow, lpMatchWrap(bottom = dp(6)))
        val score = if (sum.pass + sum.fail > 0) "Score ${sum.pass * 100 / (sum.pass + sum.fail)}% • " else ""
        info.addView(label("$score${sum.done}/${items.size} tested", 12f, color, true))
        row.addView(info, LinearLayout.LayoutParams(0, WRAP, 1f))
        frame.addView(row, FrameLayout.LayoutParams(MATCH, WRAP))
        return frame
    }

    private fun startButton(): View {
        val b = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(18))
            background = gradientBg(intArrayOf(Palette.PURPLE, Color.rgb(79, 70, 229), Palette.ACCENT), 32f)
            elevation = dpf(10f)
            isClickable = true
            setOnClickListener { startFullTest() }
        }
        b.addView(label("🚀", 30f), LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(16) })
        val t = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        t.addView(label("Start Full Test", 21f, Color.WHITE, true))
        t.addView(label("Check all ${items.size} features automatically", 13f, Color.argb(215, 255, 255, 255)))
        b.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
        b.addView(label("›", 34f, Color.WHITE, true))
        return b
    }

    private fun tintFor(k: Int): Int = when (k) {
        QuickTiles.GREEN -> Palette.PASS
        QuickTiles.PURPLE -> Palette.PURPLE
        QuickTiles.ORANGE -> Color.rgb(249, 115, 22)
        QuickTiles.CYAN -> Palette.CYAN
        QuickTiles.PINK -> Color.rgb(236, 72, 153)
        QuickTiles.YELLOW -> Color.rgb(234, 179, 8)
        else -> Palette.ACCENT
    }

    private fun addGrid(col: LinearLayout, tiles: List<QuickTile>, perRow: Int, compact: Boolean) {
        tiles.chunked(perRow).forEach { chunk ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (tile in chunk) {
                row.addView(tileView(tile, compact), LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                    marginStart = dp(4)
                    marginEnd = dp(4)
                })
            }
            repeat(perRow - chunk.size) { row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
            col.addView(row, lpMatchWrap(bottom = dp(8)))
        }
    }

    private fun tileIds(tile: QuickTile): List<String> = items.filter { tile.match(it.id) }.map { it.id }

    private fun tileStatus(ids: List<String>): TestStatus {
        if (ids.isEmpty()) return TestStatus.UNTESTED
        val st = ids.map { ResultStore.status(this, it) }
        return when {
            st.any { it == TestStatus.FAIL } -> TestStatus.FAIL
            st.all { it == TestStatus.PASS } -> TestStatus.PASS
            st.all { it != TestStatus.UNTESTED } -> TestStatus.SKIP
            else -> TestStatus.UNTESTED
        }
    }

    private fun tileView(tile: QuickTile, compact: Boolean): View {
        val tint = tintFor(tile.tintKey)
        val ids = tileIds(tile)
        val isInfo = tile.icon == "ℹ️"
        val status = if (isInfo) TestStatus.UNTESTED else tileStatus(ids)
        val frame = FrameLayout(this).apply {
            background = gradientBg(
                if (Palette.dark) intArrayOf(Palette.alpha(tint, 38), Palette.CARD) else intArrayOf(Color.WHITE, Palette.alpha(tint, 18)),
                if (compact) 16f else 20f,
                GradientDrawable_TL_BR,
                Palette.alpha(tint, if (Palette.dark) 90 else 70),
            )
            elevation = if (Palette.dark) 0f else dpf(2f)
            isClickable = true
            setOnClickListener {
                when {
                    isInfo -> startActivity(Intent(this@MainActivity, DeviceInfoActivity::class.java))
                    ids.size == 1 -> singleLauncher.launch(items.first { it.id == ids[0] }.intent(this@MainActivity))
                    ids.isNotEmpty() -> FullTestActivity.start(this@MainActivity, ids, tile.title.replace("\n", " ").removeSuffix(" Check"))
                }
            }
        }
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (compact) Gravity.CENTER_HORIZONTAL else Gravity.START
            setPadding(dp(if (compact) 4 else 12), dp(12), dp(if (compact) 4 else 10), dp(12))
        }
        c.addView(iconBadge(tile.icon, tint, if (compact) 36 else 42, if (compact) 17f else 20f))
        if (compact) {
            c.addView(label(tile.title, 11f, Palette.TEXT, true).apply { gravity = Gravity.CENTER }, lpMatchWrap(top = dp(6)))
        } else {
            val bottom = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            bottom.addView(label(tile.title, 13f, Palette.TEXT, true), LinearLayout.LayoutParams(0, WRAP, 1f))
            bottom.addView(label("›", 15f, Palette.TEXT, true).apply {
                gravity = Gravity.CENTER
                background = roundedBg(Palette.alpha(Palette.TEXT, 22), dpf(11f))
            }, LinearLayout.LayoutParams(dp(22), dp(22)))
            c.addView(bottom, lpMatchWrap(top = dp(10)))
        }
        frame.addView(c, FrameLayout.LayoutParams(MATCH, WRAP))
        if (status != TestStatus.UNTESTED) {
            val dot = label(if (status == TestStatus.PASS) "✓" else if (status == TestStatus.FAIL) "✕" else "–", 10f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER
                background = roundedBg(statusColor(status), dpf(9f))
            }
            frame.addView(dot, FrameLayout.LayoutParams(dp(18), dp(18), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(8)
                marginEnd = dp(8)
            })
        }
        return frame
    }

    // ------------------------------------------------------------ history

    private fun buildHistory(col: LinearLayout) {
        col.addView(label("Test History", 26f, Palette.TEXT, true))
        val sum = summary()
        col.addView(label("Tested ${sum.done} / ${items.size}   ✓ ${sum.pass}   ✕ ${sum.fail}   ⏭ ${sum.skip}", 13f, Palette.MUTED), lpMatchWrap(top = dp(4), bottom = dp(8)))
        val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = items.size
            progress = sum.done
        }
        col.addView(pb, LinearLayout.LayoutParams(MATCH, dp(10)).apply { bottomMargin = dp(8) })
        col.addView(label("Tap any test to run it again.", 12f, Palette.MUTED), lpMatchWrap(bottom = dp(4)))

        val fmt = SimpleDateFormat("dd MMM, HH:mm", Locale.US)
        for ((cat, group) in items.groupBy { it.category }) {
            col.addView(label(cat.uppercase(), 12f, Palette.ACCENT, true), lpMatchWrap(top = dp(16), bottom = dp(6)))
            val box = glassCard(Palette.ACCENT, 18f).apply { setPadding(dp(4), dp(4), dp(4), dp(4)) }
            for (item in group) {
                val s = ResultStore.status(this, item.id)
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    isClickable = true
                    setOnClickListener { singleLauncher.launch(item.intent(this@MainActivity)) }
                }
                row.addView(iconBadge(item.icon, Palette.ACCENT, 36, 17f).apply {
                    (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(10)
                })
                val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                texts.addView(label(item.title, 14f, Palette.TEXT, true))
                val t = ResultStore.time(this, item.id)
                val note = ResultStore.note(this, item.id)
                val sub = listOfNotNull(if (t > 0) fmt.format(Date(t)) else null, note?.takeIf { it.isNotBlank() }).joinToString(" • ")
                if (sub.isNotEmpty()) texts.addView(label(sub, 11f, Palette.MUTED))
                row.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
                row.addView(pill(statusLabel(s), if (s == TestStatus.UNTESTED) Palette.MUTED else statusColor(s), s != TestStatus.UNTESTED).apply {
                    textSize = 10f
                })
                box.addView(row)
            }
            col.addView(box, lpMatchWrap())
        }
    }

    // ------------------------------------------------------------ more / settings

    private fun buildMore(col: LinearLayout) {
        col.addView(label("Settings", 26f, Palette.TEXT, true), lpMatchWrap(bottom = dp(14)))

        val theme = glassCard(Palette.PURPLE, 18f)
        theme.addView(label("Appearance", 16f, Palette.TEXT, true))
        theme.addView(label("Choose light (white), dark, or follow the phone setting.", 12f, Palette.MUTED), lpMatchWrap(bottom = dp(10)))
        val seg = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = roundedBg(Palette.CARD_ALT, dpf(16f))
        }
        val current = ThemeManager.mode(this)
        for ((mode, name) in listOf(ThemeManager.SYSTEM to "📱 System", ThemeManager.LIGHT to "☀️ Light", ThemeManager.DARK to "🌙 Dark")) {
            val on = mode == current
            seg.addView(label(name, 14f, if (on) Color.WHITE else Palette.TEXT, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(12))
                background = if (on) gradientBg(intArrayOf(Palette.PURPLE, Palette.ACCENT), 13f) else null
                isClickable = true
                setOnClickListener { if (!on) ThemeManager.setMode(this@MainActivity, mode) }
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        theme.addView(seg, lpMatchWrap())
        col.addView(theme, lpMatchWrap(bottom = dp(12)))

        fun menuRow(icon: String, title: String, sub: String, tint: Int, onClick: () -> Unit) {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = roundedBg(Palette.CARD, dpf(18f), Palette.STROKE, dp(1))
                isClickable = true
                setOnClickListener { onClick() }
            }
            r.addView(iconBadge(icon, tint, 40, 19f).apply { (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(12) })
            val tx = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            tx.addView(label(title, 15f, Palette.TEXT, true))
            tx.addView(label(sub, 12f, Palette.MUTED))
            r.addView(tx, LinearLayout.LayoutParams(0, WRAP, 1f))
            r.addView(label("›", 22f, Palette.MUTED, true))
            col.addView(r, lpMatchWrap(bottom = dp(10)))
        }
        menuRow("ℹ️", "Device information", "Full hardware & software specs", Palette.ACCENT) {
            startActivity(Intent(this, DeviceInfoActivity::class.java))
        }
        menuRow("📊", "Diagnostic report", "Score, faults and PDF export", Palette.PURPLE) {
            startActivity(Intent(this, ReportActivity::class.java))
        }
        menuRow("↺", "Reset all results", "Clear saved test results", Palette.FAIL) { confirmReset() }

        col.addView(label("GX Device Tester • v1.0\nAll checks run on the phone. No data is sent anywhere.", 12f, Palette.MUTED).apply {
            gravity = Gravity.CENTER
        }, lpMatchWrap(top = dp(16)))
    }

    // ------------------------------------------------------------ actions

    private fun toggleTheme() {
        ThemeManager.setMode(this, if (Palette.dark) ThemeManager.LIGHT else ThemeManager.DARK)
    }

    private fun startFullTest() {
        val untested = items.filter { ResultStore.status(this, it.id) == TestStatus.UNTESTED }
        if (untested.size == items.size || untested.isEmpty()) {
            FullTestActivity.start(this, items.map { it.id })
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Start full test")
            .setItems(arrayOf("Continue with untested (${untested.size})", "Test everything again (${items.size})")) { _, which ->
                FullTestActivity.start(this, (if (which == 0) untested else items).map { it.id })
            }
            .show()
    }

    private fun confirmReset() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reset all results?")
            .setMessage("All saved test results will be cleared.")
            .setPositiveButton("Reset") { _, _ ->
                ResultStore.clear(this)
                showTab(tab)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun batteryPct(): Int {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return -1
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (level >= 0 && scale > 0) level * 100 / scale else -1
    }

    private companion object {
        val GradientDrawable_TL_BR = android.graphics.drawable.GradientDrawable.Orientation.TL_BR
    }
}
