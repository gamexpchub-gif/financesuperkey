package com.gamex.devicetester

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.gamex.devicetester.core.DeviceInfo
import com.gamex.devicetester.core.ResultStore
import com.gamex.devicetester.core.TestItem
import com.gamex.devicetester.core.TestRegistry
import com.gamex.devicetester.core.TestStatus
import com.gamex.devicetester.ui.MATCH
import com.gamex.devicetester.ui.Palette
import com.gamex.devicetester.ui.WRAP
import com.gamex.devicetester.ui.actionButton
import com.gamex.devicetester.ui.card
import com.gamex.devicetester.ui.dp
import com.gamex.devicetester.ui.dpf
import com.gamex.devicetester.ui.label
import com.gamex.devicetester.ui.lpMatchWrap
import com.gamex.devicetester.ui.roundedBg
import com.gamex.devicetester.ui.statusColor
import com.gamex.devicetester.ui.statusLabel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Dashboard: device summary, progress, "Start full test" wizard and every individual test. */
class MainActivity : AppCompatActivity() {

    private lateinit var items: List<TestItem>
    private lateinit var progressText: TextView
    private lateinit var progressBar: ProgressBar
    private val statusViews = HashMap<String, TextView>()
    private var wizardQueue: List<TestItem> = emptyList()
    private var wizardIndex = -1

    private val testLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (wizardIndex >= 0) {
            if (res.resultCode == RESULT_OK) {
                wizardIndex++
                launchWizardStep()
            } else {
                wizardIndex = -1
                Toast.makeText(this, "Full test stopped", Toast.LENGTH_SHORT).show()
            }
        }
        refresh()
    }

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        wizardIndex = 0
        launchWizardStep()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        items = TestRegistry.build(this)
        savedInstanceState?.let { s ->
            wizardIndex = s.getInt("wizardIndex", -1)
            val ids = s.getStringArrayList("wizardIds").orEmpty()
            wizardQueue = ids.mapNotNull { id -> items.firstOrNull { it.id == id } }
        }
        buildUi()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("wizardIndex", wizardIndex)
        outState.putStringArrayList("wizardIds", ArrayList(wizardQueue.map { it.id }))
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun buildUi() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(24))
        }
        val scroll = ScrollView(this).apply { setBackgroundColor(Palette.BG) }
        scroll.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
        setContentView(scroll)

        col.addView(label("GX Device Tester", 26f, Palette.TEXT, true))
        col.addView(label("Professional phone hardware diagnostics", 13f, Palette.MUTED), lpMatchWrap(bottom = dp(14)))

        // Device summary
        val dev = card()
        dev.addView(label("📱 ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}", 18f, Palette.TEXT, true))
        dev.addView(label(DeviceInfo.shortSummary(this) + "  •  Battery ${batteryPct()}%", 13f, Palette.MUTED), lpMatchWrap(top = dp(4)))
        col.addView(dev, lpMatchWrap(bottom = dp(12)))

        // Progress
        val prog = card()
        progressText = label("", 14f, Palette.TEXT, true)
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        prog.addView(progressText)
        prog.addView(progressBar, LinearLayout.LayoutParams(MATCH, dp(14)).apply { topMargin = dp(8) })
        col.addView(prog, lpMatchWrap(bottom = dp(12)))

        col.addView(actionButton("▶  Start full test (step by step)") { startWizard() }.apply {
            minHeight = dp(58)
            textSize = 17f
        }, lpMatchWrap(bottom = dp(8)))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(actionButton("📄 Report", Palette.CARD_ALT) {
            startActivity(Intent(this, ReportActivity::class.java))
        }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(6) })
        row.addView(actionButton("ℹ Device info", Palette.CARD_ALT) {
            startActivity(Intent(this, DeviceInfoActivity::class.java))
        }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(6) })
        row.addView(actionButton("↺ Reset", Palette.CARD_ALT) { confirmReset() }, LinearLayout.LayoutParams(0, WRAP, 0.8f))
        col.addView(row, lpMatchWrap(bottom = dp(8)))

        // Tests grouped by category
        for ((cat, group) in items.groupBy { it.category }) {
            col.addView(label(cat.uppercase(), 12f, Palette.ACCENT, true), lpMatchWrap(top = dp(14), bottom = dp(6)))
            val box = card().apply { setPadding(dp(4), dp(4), dp(4), dp(4)) }
            for (item in group) box.addView(testRow(item))
            col.addView(box, lpMatchWrap())
        }
        col.addView(label("Tap any test to run it on its own. Results are saved and included in the report.", 12f, Palette.MUTED),
            lpMatchWrap(top = dp(16)))
    }

    private fun testRow(item: TestItem): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isClickable = true
            isFocusable = true
            val attrs = intArrayOf(android.R.attr.selectableItemBackground)
            val ta = context.obtainStyledAttributes(attrs)
            foreground = ta.getDrawable(0)
            ta.recycle()
            setOnClickListener {
                wizardIndex = -1
                testLauncher.launch(item.intent(this@MainActivity))
            }
        }
        row.addView(label(item.icon, 20f), LinearLayout.LayoutParams(dp(36), WRAP))
        row.addView(label(item.title, 15f, Palette.TEXT), LinearLayout.LayoutParams(0, WRAP, 1f))
        val chip = label("", 11f, android.graphics.Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(3), dp(8), dp(3))
        }
        row.addView(chip, LinearLayout.LayoutParams(WRAP, WRAP))
        statusViews[item.id] = chip
        return row
    }

    private fun refresh() {
        if (!::progressText.isInitialized) return
        var pass = 0
        var fail = 0
        var skip = 0
        for (item in items) {
            val s = ResultStore.status(this, item.id)
            when (s) {
                TestStatus.PASS -> pass++
                TestStatus.FAIL -> fail++
                TestStatus.SKIP -> skip++
                TestStatus.UNTESTED -> Unit
            }
            statusViews[item.id]?.let { chip ->
                chip.text = statusLabel(s)
                chip.background = roundedBg(if (s == TestStatus.UNTESTED) Palette.CARD_ALT else statusColor(s), dpf(9f))
            }
        }
        val done = pass + fail + skip
        progressBar.max = items.size
        progressBar.progress = done
        val score = if (pass + fail > 0) " • Score ${pass * 100 / (pass + fail)}%" else ""
        progressText.text = "Tested $done / ${items.size}   ✓ $pass   ✕ $fail   ⏭ $skip$score"
    }

    private fun startWizard() {
        val untested = items.filter { ResultStore.status(this, it.id) == TestStatus.UNTESTED }
        if (untested.size == items.size || untested.isEmpty()) {
            beginWizard(items)
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Start full test")
            .setItems(arrayOf("Continue with untested (${untested.size})", "Test everything again (${items.size})")) { _, which ->
                if (which == 0) beginWizard(untested) else beginWizard(items)
            }
            .show()
    }

    private fun beginWizard(queue: List<TestItem>) {
        wizardQueue = queue
        val perms = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
        )
        if (Build.VERSION.SDK_INT >= 29) perms += Manifest.permission.ACTIVITY_RECOGNITION
        if (Build.VERSION.SDK_INT >= 31) {
            perms += Manifest.permission.BLUETOOTH_SCAN
            perms += Manifest.permission.BLUETOOTH_CONNECT
        }
        permLauncher.launch(perms.toTypedArray())
    }

    private fun launchWizardStep() {
        if (wizardIndex < 0) return
        if (wizardIndex >= wizardQueue.size) {
            wizardIndex = -1
            startActivity(Intent(this, ReportActivity::class.java))
            return
        }
        val item = wizardQueue[wizardIndex]
        val step = "STEP ${wizardIndex + 1} OF ${wizardQueue.size}  •  ${item.category.uppercase()}"
        testLauncher.launch(item.intent(this, step))
    }

    private fun confirmReset() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reset all results?")
            .setMessage("All saved test results will be cleared.")
            .setPositiveButton("Reset") { _, _ ->
                ResultStore.clear(this)
                refresh()
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
}
