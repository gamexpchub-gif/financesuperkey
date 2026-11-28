package com.gamex.devicetester

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import com.gamex.devicetester.core.BaseTestActivity
import com.gamex.devicetester.core.TestGroups
import com.gamex.devicetester.core.TestItem
import com.gamex.devicetester.core.TestRegistry
import com.gamex.devicetester.core.TestStatus
import com.gamex.devicetester.ui.MATCH
import com.gamex.devicetester.ui.Palette
import com.gamex.devicetester.ui.HoloBackground
import com.gamex.devicetester.ui.PhoneArtView
import com.gamex.devicetester.ui.PulseLogo
import com.gamex.devicetester.ui.ProgressRing
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
import com.gamex.devicetester.ui.shieldIcon
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Automated full-test runner: launches every test in sequence, auto-advances, and shows live
 * progress (ring, ETA, passed count, per-group status).
 */
class FullTestActivity : ThemedActivity() {

    companion object {
        const val EXTRA_IDS = "ids"
        const val EXTRA_RUN_TITLE = "run_title"
        private const val SECONDS_PER_TEST = 20

        fun start(c: Context, ids: List<String>, title: String = "Full Test") {
            c.startActivity(Intent(c, FullTestActivity::class.java)
                .putStringArrayListExtra(EXTRA_IDS, ArrayList(ids))
                .putExtra(EXTRA_RUN_TITLE, title))
        }
    }

    private enum class RunState { RUNNING, STOPPED, DONE }

    private lateinit var queue: List<TestItem>
    private var index = 0
    private val results = HashMap<String, TestStatus>()
    private var state = RunState.RUNNING
    private var runTitle = "Full Test"

    private lateinit var ring: ProgressRing
    private lateinit var percent: TextView
    private lateinit var eta: TextView
    private lateinit var passed: TextView
    private lateinit var heroTitle: TextView
    private lateinit var heroSub: TextView
    private lateinit var groupList: LinearLayout
    private lateinit var banner: LinearLayout
    private lateinit var bottomArea: FrameLayout
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val item = queue.getOrNull(index) ?: return@registerForActivityResult
        if (res.resultCode == RESULT_OK) {
            val st = res.data?.getStringExtra(BaseTestActivity.EXTRA_STATUS)
            results[item.id] = try {
                TestStatus.valueOf(st ?: "SKIP")
            } catch (e: Exception) {
                TestStatus.SKIP
            }
            index++
            if (index >= queue.size) {
                state = RunState.DONE
                render()
            } else {
                render()
                handler.postDelayed({ if (state == RunState.RUNNING) launchCurrent() }, 700)
            }
        } else {
            state = RunState.STOPPED
            render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runTitle = intent.getStringExtra(EXTRA_RUN_TITLE) ?: "Full Test"
        val all = TestRegistry.build(this)
        val ids = intent.getStringArrayListExtra(EXTRA_IDS)
        val chosen = if (ids.isNullOrEmpty()) all else ids.mapNotNull { id -> all.firstOrNull { it.id == id } }
        queue = TestGroups.sortForRun(chosen)

        if (savedInstanceState != null) {
            index = savedInstanceState.getInt("index", 0)
            state = RunState.values()[savedInstanceState.getInt("state", 0)]
            savedInstanceState.getStringArrayList("res")?.forEach { e ->
                val (k, v) = e.split("=", limit = 2).let { it[0] to it.getOrElse(1) { "SKIP" } }
                results[k] = TestStatus.valueOf(v)
            }
        }

        buildUi()
        render()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                confirmStop()
            }
        })
        if (savedInstanceState == null && queue.isNotEmpty()) {
            handler.postDelayed({ if (state == RunState.RUNNING) launchCurrent() }, 900)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("index", index)
        outState.putInt("state", state.ordinal)
        outState.putStringArrayList("res", ArrayList(results.map { "${it.key}=${it.value.name}" }))
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun launchCurrent() {
        val item = queue.getOrNull(index) ?: return
        val g = TestGroups.group(TestGroups.groupKey(item.id))
        launcher.launch(item.intent(this, "STEP ${index + 1} OF ${queue.size}  •  ${g.title.uppercase()}"))
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    // ---------------------------------------------------------------- UI

    private fun buildUi() {
        val stack = FrameLayout(this)
        stack.addView(HoloBackground(this), FrameLayout.LayoutParams(MATCH, MATCH))
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        stack.addView(root, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(stack)

        // Top bar
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
        }
        top.addView(circleButton("‹", 44) { confirmStop() }.apply { textSize = 26f })
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(PulseLogo(context), LinearLayout.LayoutParams(dp(40), dp(28)).apply { marginEnd = dp(8) })
            val bc = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            bc.addView(brandTitle(21f))
            bc.addView(label("Ultimate Device Health Checker", 11f, Palette.MUTED))
            addView(bc)
        }
        top.addView(brand, LinearLayout.LayoutParams(0, WRAP, 1f))
        top.addView(View(this), LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(top, lpMatchWrap())

        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(16))
        }
        scroll.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
        root.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        // Hero
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = gradientBg(
                if (Palette.dark) intArrayOf(Color.rgb(14, 28, 72), Color.rgb(10, 16, 40), Color.rgb(24, 20, 70))
                else intArrayOf(Color.rgb(239, 246, 255), Color.WHITE, Color.rgb(245, 243, 255)),
                22f, strokeColor = Palette.alpha(Palette.ACCENT, 110),
            )
        }
        hero.addView(PhoneArtView(this), LinearLayout.LayoutParams(dp(62), dp(104)).apply { marginEnd = dp(16) })
        val ht = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heroTitle = label("", 21f, Palette.TEXT, true)
        heroSub = label("", 13f, Palette.MUTED)
        ht.addView(heroTitle)
        ht.addView(heroSub, lpMatchWrap(top = dp(4)))
        hero.addView(ht, LinearLayout.LayoutParams(0, WRAP, 1f))
        col.addView(hero, lpMatchWrap(bottom = dp(16)))

        // Ring + stats
        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val ringBox = FrameLayout(this)
        ring = ProgressRing(this)
        ringBox.addView(ring, FrameLayout.LayoutParams(MATCH, MATCH))
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        center.addView(label("🧪", 20f).apply { gravity = Gravity.CENTER })
        percent = label("0%", 34f, Palette.TEXT, true).apply { gravity = Gravity.CENTER }
        center.addView(percent)
        center.addView(label("Complete", 14f, Palette.ACCENT, true).apply { gravity = Gravity.CENTER })
        ringBox.addView(center, FrameLayout.LayoutParams(MATCH, MATCH))
        stats.addView(ringBox, LinearLayout.LayoutParams(0, dp(176), 1.1f))

        val sc = glassCard(Palette.ACCENT, 18f)
        val etaRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        etaRow.addView(label("⏱", 22f), LinearLayout.LayoutParams(dp(32), WRAP))
        val etaCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        etaCol.addView(label("Estimated time", 12f, Palette.MUTED))
        eta = label("00:00:00", 18f, Palette.TEXT, true)
        etaCol.addView(eta)
        etaRow.addView(etaCol)
        sc.addView(etaRow)
        sc.addView(View(this).apply { setBackgroundColor(Palette.STROKE) }, LinearLayout.LayoutParams(MATCH, dp(1)).apply {
            topMargin = dp(12)
            bottomMargin = dp(12)
        })
        val pRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        pRow.addView(shieldIcon(Palette.PASS, 26), LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(6) })
        val pCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        pCol.addView(label("Tests passed", 12f, Palette.MUTED))
        passed = label("0 / 0", 18f, Palette.TEXT, true)
        pCol.addView(passed)
        pRow.addView(pCol)
        sc.addView(pRow)
        stats.addView(sc, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(8) })
        col.addView(stats, lpMatchWrap(bottom = dp(16)))

        groupList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(groupList, lpMatchWrap())

        // Bottom area (banner + main button)
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        banner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        bottom.addView(banner, lpMatchWrap(bottom = dp(10)))
        bottomArea = FrameLayout(this)
        bottom.addView(bottomArea, lpMatchWrap())
        root.addView(bottom, lpMatchWrap())
    }

    private fun render() {
        val total = queue.size
        val done = index.coerceAtMost(total)
        val frac = if (total == 0) 1f else done.toFloat() / total
        ring.setProgress(frac)
        percent.text = "${(frac * 100).toInt()}%"
        val pass = results.values.count { it == TestStatus.PASS }
        val fail = results.values.count { it == TestStatus.FAIL }
        passed.text = "$pass / $total"
        val secs = (total - done) * SECONDS_PER_TEST
        eta.text = String.format("%02d:%02d:%02d", secs / 3600, (secs / 60) % 60, secs % 60)

        when (state) {
            RunState.RUNNING -> {
                heroTitle.text = "$runTitle in Progress…"
                heroSub.text = "Testing all components of your device step by step. Many tests pass automatically."
            }
            RunState.STOPPED -> {
                heroTitle.text = "$runTitle Paused"
                heroSub.text = "$done of $total tests done. Resume any time — results are saved."
            }
            RunState.DONE -> {
                heroTitle.text = if (fail == 0) "All Tests Complete ✓" else "Test Complete — $fail issue(s)"
                heroSub.text = "$pass passed • $fail failed • ${results.values.count { it == TestStatus.SKIP }} skipped"
            }
        }

        renderGroups()
        renderBottom(fail)
    }

    private fun renderGroups() {
        groupList.removeAllViews()
        val current = queue.getOrNull(index)?.id
        val byGroup = queue.groupBy { TestGroups.groupKey(it.id) }
        for (g in TestGroups.ordered) {
            val items = byGroup[g.key] ?: continue
            val statuses = items.mapNotNull { results[it.id] }
            val isCurrent = state != RunState.DONE && current != null && items.any { it.id == current }
            val allDone = statuses.size == items.size
            val (pillText, color, filled) = when {
                isCurrent && state == RunState.RUNNING -> Triple("Testing…", Palette.ACCENT, true)
                allDone && statuses.any { it == TestStatus.FAIL } -> Triple("FAILED", Palette.FAIL, false)
                allDone && statuses.all { it == TestStatus.SKIP } -> Triple("SKIPPED", Palette.SKIP, false)
                allDone -> Triple("PASSED", Palette.PASS, false)
                statuses.isNotEmpty() -> Triple("Partial", Palette.SKIP, false)
                else -> Triple("Pending…", Palette.MUTED, false)
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = if (isCurrent) {
                    roundedBg(Palette.alpha(Palette.ACCENT, if (Palette.dark) 40 else 22), dpf(18f), Palette.ACCENT, dp(2))
                } else {
                    roundedBg(Palette.CARD, dpf(18f), Palette.STROKE, dp(1))
                }
            }
            // status circle
            val indicator: View = when {
                isCurrent && state == RunState.RUNNING -> ProgressBar(this).apply {
                    indeterminateTintList = ColorStateList.valueOf(Palette.ACCENT)
                }
                allDone || pillText == "Partial" -> label(
                    if (pillText == "FAILED") "✕" else if (pillText == "SKIPPED") "–" else "✓", 15f, Color.WHITE, true,
                ).apply {
                    gravity = Gravity.CENTER
                    background = roundedBg(color, dpf(15f))
                }
                else -> View(this).apply { background = roundedBg(Color.TRANSPARENT, dpf(15f), Palette.MUTED, dp(2)) }
            }
            row.addView(indicator, LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(12) })
            row.addView(iconBadge(g.icon, Palette.ACCENT, 42, 19f).apply {
                (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(12)
            })
            val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(label(g.title, 15f, Palette.TEXT, true))
            val count = if (items.size > 1) "  •  ${statuses.size}/${items.size}" else ""
            texts.addView(label(g.subtitle + count, 12f, if (isCurrent) Palette.ACCENT else Palette.MUTED))
            row.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
            row.addView(pill(pillText, color, filled))
            groupList.addView(row, lpMatchWrap(bottom = dp(8)))
        }
    }

    private fun renderBottom(fail: Int) {
        banner.removeAllViews()
        bottomArea.removeAllViews()
        val (bTitle, bSub, bColor) = when (state) {
            RunState.RUNNING -> Triple("Please wait…", "Follow the on-screen steps. Don't close the app while testing.", Palette.PASS)
            RunState.STOPPED -> Triple("Test paused", "Tap Resume to continue from the next test.", Palette.SKIP)
            RunState.DONE -> if (fail == 0) Triple("Device is healthy", "Every tested component passed.", Palette.PASS)
            else Triple("Attention needed", "$fail component(s) failed — see the report.", Palette.FAIL)
        }
        banner.background = gradientBg(
            intArrayOf(Palette.alpha(bColor, if (Palette.dark) 40 else 25), Palette.alpha(Palette.PURPLE, if (Palette.dark) 50 else 25)),
            18f, strokeColor = Palette.alpha(bColor, 140),
        )
        banner.addView(shieldIcon(bColor, 36), LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(12) })
        val bt = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bt.addView(label(bTitle, 16f, Palette.TEXT, true))
        bt.addView(label(bSub, 12f, Palette.MUTED))
        banner.addView(bt, LinearLayout.LayoutParams(0, WRAP, 1f))

        when (state) {
            RunState.RUNNING -> bottomArea.addView(bigButton("■  Stop Test", outlined = true) { confirmStop() })
            RunState.STOPPED -> {
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                row.addView(bigButton("📄 Report", outlined = true) { openReport() }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(8) })
                row.addView(bigButton("▶  Resume", outlined = false) {
                    state = RunState.RUNNING
                    render()
                    launchCurrent()
                }, LinearLayout.LayoutParams(0, WRAP, 1.3f))
                bottomArea.addView(row)
            }
            RunState.DONE -> bottomArea.addView(bigButton("📄  View Full Report", outlined = false) { openReport() })
        }
    }

    private fun bigButton(text: String, outlined: Boolean, onClick: () -> Unit): TextView =
        label(text, 17f, if (outlined) Palette.TEXT else Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = if (outlined) {
                roundedBg(Palette.CARD, dpf(28f), Palette.PURPLE, dp(2))
            } else {
                gradientBg(intArrayOf(Palette.PURPLE, Palette.ACCENT), 28f)
            }
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun openReport() {
        startActivity(Intent(this, ReportActivity::class.java))
        if (state == RunState.DONE) finish()
    }

    private fun confirmStop() {
        if (state != RunState.RUNNING) {
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Stop the test?")
            .setMessage("Results so far are saved. You can resume later from the home screen.")
            .setPositiveButton("Stop") { _, _ ->
                state = RunState.STOPPED
                handler.removeCallbacksAndMessages(null)
                render()
            }
            .setNegativeButton("Continue", null)
            .show()
    }
}
