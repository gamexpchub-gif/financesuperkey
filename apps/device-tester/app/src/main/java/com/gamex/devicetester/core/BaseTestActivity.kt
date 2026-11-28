package com.gamex.devicetester.core

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gamex.devicetester.ui.MATCH
import com.gamex.devicetester.ui.Palette
import com.gamex.devicetester.ui.WRAP
import com.gamex.devicetester.ui.actionButton
import com.gamex.devicetester.ui.dp
import com.gamex.devicetester.ui.label
import com.gamex.devicetester.ui.lpMatchWrap
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.random.Random

/**
 * Common frame for every hardware test: title, step counter, instructions, a content
 * area, a live status line and Fail / Skip / Pass buttons. Tests can also auto-pass.
 */
abstract class BaseTestActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TEST_ID = "extra_test_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_STEP = "extra_step"
        const val EXTRA_STATUS = "extra_status"
        const val RESULT_STOP_WIZARD = Activity.RESULT_FIRST_USER + 1
    }

    protected lateinit var testId: String
    protected lateinit var testTitle: String
    protected lateinit var content: FrameLayout
    private var instructionView: TextView? = null
    private var statusView: TextView? = null
    protected var passButton: MaterialButton? = null
    protected var failButton: MaterialButton? = null
    protected var skipButton: MaterialButton? = null
    protected val handler = Handler(Looper.getMainLooper())
    private var done = false
    private var autoPending = false

    protected open val fullscreen: Boolean get() = false

    private var permissionCallback: ((Boolean) -> Unit)? = null
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val cb = permissionCallback
            permissionCallback = null
            cb?.invoke(result.values.all { it })
        }

    abstract fun onCreateTest(savedInstanceState: Bundle?)

    /** Extra detail stored with the result (shown in the report). */
    protected open fun currentNote(): String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        testId = intent.getStringExtra(EXTRA_TEST_ID) ?: javaClass.simpleName
        testTitle = intent.getStringExtra(EXTRA_TITLE) ?: ""
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (fullscreen) buildFullscreen() else buildStandard()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitDialog()
            }
        })
        onCreateTest(savedInstanceState)
    }

    private fun buildStandard() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.BG)
            setPadding(dp(16), dp(14), dp(16), dp(12))
        }
        intent.getStringExtra(EXTRA_STEP)?.let { root.addView(label(it, 12f, Palette.ACCENT, true)) }
        root.addView(label(testTitle, 22f, Palette.TEXT, true))
        instructionView = label("", 14f, Palette.MUTED).also { root.addView(it, lpMatchWrap(top = dp(4))) }

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(MATCH, 0, 1f).apply {
            topMargin = dp(12)
            bottomMargin = dp(8)
        })

        statusView = label("", 15f, Palette.TEXT, true).apply { gravity = Gravity.CENTER }
        root.addView(statusView, lpMatchWrap(bottom = dp(8)))

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val f = actionButton("✕ Fail", Palette.FAIL) { finishWith(TestStatus.FAIL) }
        val s = actionButton("Skip", Palette.CARD_ALT) { finishWith(TestStatus.SKIP) }
        val p = actionButton("✓ Pass", Palette.PASS) { finishWith(TestStatus.PASS) }
        bar.addView(f, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(6) })
        bar.addView(s, LinearLayout.LayoutParams(0, WRAP, 0.8f).apply { marginEnd = dp(6) })
        bar.addView(p, LinearLayout.LayoutParams(0, WRAP, 1f))
        failButton = f
        skipButton = s
        passButton = p
        root.addView(bar, lpMatchWrap())
        setContentView(root)
    }

    private fun buildFullscreen() {
        content = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(content)
        if (Build.VERSION.SDK_INT >= 28) {
            val attrs = window.attributes
            attrs.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = attrs
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, content).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** A scrollable vertical column filling the content area. */
    protected fun scrollColumn(): LinearLayout {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val sv = ScrollView(this).apply { isFillViewport = true }
        sv.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
        content.addView(sv, FrameLayout.LayoutParams(MATCH, MATCH))
        return col
    }

    protected fun setInstruction(text: CharSequence) {
        instructionView?.text = text
    }

    protected fun setStatus(text: CharSequence, color: Int = Palette.TEXT) {
        statusView?.text = text
        statusView?.setTextColor(color)
    }

    protected fun isTestDone(): Boolean = done || autoPending

    fun finishWith(status: TestStatus, note: String? = currentNote()) {
        if (done) return
        done = true
        ResultStore.save(this, testId, status, note)
        setResult(RESULT_OK, Intent().putExtra(EXTRA_STATUS, status.name))
        finish()
    }

    protected fun autoPass(note: String? = currentNote(), delayMs: Long = 1000) {
        if (done || autoPending) return
        autoPending = true
        setStatus("✓ PASSED", Palette.PASS)
        handler.postDelayed({ finishWith(TestStatus.PASS, note) }, delayMs)
    }

    protected fun autoFail(note: String?, delayMs: Long = 1500) {
        if (done || autoPending) return
        autoPending = true
        setStatus("✕ FAILED — $note", Palette.FAIL)
        handler.postDelayed({ finishWith(TestStatus.FAIL, note) }, delayMs)
    }

    protected fun hasPermission(p: String): Boolean =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    protected fun withPermissions(vararg perms: String, onResult: (Boolean) -> Unit) {
        val missing = perms.filter { !hasPermission(it) }
        if (missing.isEmpty()) {
            onResult(true)
            return
        }
        permissionCallback = onResult
        permissionLauncher.launch(missing.toTypedArray())
    }

    protected fun safeStart(intent: Intent, fallback: Intent? = null) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            if (fallback != null) {
                try {
                    startActivity(fallback)
                } catch (e2: Exception) {
                    setStatus("Could not open screen on this device", Palette.SKIP)
                }
            } else {
                setStatus("Could not open screen on this device", Palette.SKIP)
            }
        }
    }

    private fun showExitDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(testTitle.ifEmpty { "Test" })
            .setItems(arrayOf("✕  Mark as FAILED", "⏭  Skip this test", "⏹  Stop / exit without saving", "↩  Continue testing")) { _, which ->
                when (which) {
                    0 -> finishWith(TestStatus.FAIL)
                    1 -> finishWith(TestStatus.SKIP)
                    2 -> {
                        setResult(RESULT_STOP_WIZARD)
                        finish()
                    }
                }
            }
            .show()
    }

    /**
     * Objective "count" check used for speaker, earpiece, vibration and flash: the app emits a
     * random number of pulses and the tester must enter the right count, so the result cannot be
     * faked by just tapping Pass.
     */
    protected fun addCountQuiz(
        parent: LinearLayout,
        noun: String,
        playLabel: String,
        play: (count: Int, done: () -> Unit) -> Unit,
    ) {
        var expected = 0
        var wrong = 0
        val choices = mutableListOf<MaterialButton>()
        lateinit var playBtn: MaterialButton

        fun setChoicesEnabled(on: Boolean) {
            for (b in choices) {
                b.isEnabled = on
                b.alpha = if (on) 1f else 0.4f
            }
        }

        playBtn = actionButton(playLabel) {
            expected = Random.nextInt(2, 6)
            setChoicesEnabled(false)
            playBtn.isEnabled = false
            setStatus("Count the $noun…", Palette.MUTED)
            play(expected) {
                playBtn.isEnabled = true
                playBtn.text = "↻ Play again (new count)"
                setChoicesEnabled(true)
                setStatus("How many $noun?", Palette.TEXT)
            }
        }
        parent.addView(playBtn, lpMatchWrap(bottom = dp(12)))
        parent.addView(label("How many $noun did you notice?", 14f, Palette.MUTED), lpMatchWrap(bottom = dp(8)))
        for (rowStart in listOf(1, 4)) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (n in rowStart until rowStart + 3) {
                val b = actionButton(n.toString(), Palette.CARD_ALT) {
                    if (expected != 0 && !isTestDone()) {
                        if (n == expected) {
                            autoPass("Verified: $expected $noun counted correctly")
                        } else {
                            wrong++
                            setChoicesEnabled(false)
                            if (wrong >= 3) {
                                finishWith(TestStatus.FAIL, "Wrong $noun count 3 times")
                            } else {
                                setStatus("✕ Wrong (it was $expected). Play again — ${3 - wrong} tries left", Palette.FAIL)
                            }
                        }
                    }
                }
                b.textSize = 20f
                choices += b
                row.addView(b, LinearLayout.LayoutParams(0, dp(56), 1f).apply {
                    marginStart = dp(4)
                    marginEnd = dp(4)
                })
            }
            parent.addView(row, lpMatchWrap(bottom = dp(8)))
        }
        setChoicesEnabled(false)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
