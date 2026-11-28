package com.gamex.devicetester.core

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.OvershootInterpolator
import com.gamex.devicetester.ui.HoloBackground
import com.gamex.devicetester.ui.HoloView
import com.gamex.devicetester.ui.PulseLogo
import com.gamex.devicetester.ui.ScanBar
import com.gamex.devicetester.ui.brandTitle
import com.gamex.devicetester.ui.gradientBg
import com.gamex.devicetester.ui.iconBadge
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import com.gamex.devicetester.ui.ThemedActivity
import com.gamex.devicetester.ui.circleButton
import com.gamex.devicetester.ui.dpf
import com.gamex.devicetester.ui.roundedBg
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
abstract class BaseTestActivity : ThemedActivity() {

    companion object {
        const val EXTRA_TEST_ID = "extra_test_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_STEP = "extra_step"
        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_ICON = "extra_icon"
        const val RESULT_STOP_WIZARD = Activity.RESULT_FIRST_USER + 1
    }

    protected lateinit var testId: String
    protected lateinit var testTitle: String
    protected lateinit var content: FrameLayout
    private var instructionView: TextView? = null
    private var statusView: TextView? = null
    private var subtitleView: TextView? = null
    private var scanBar: ScanBar? = null
    protected var holo: HoloView? = null
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
        val stack = FrameLayout(this)
        stack.addView(HoloBackground(this), FrameLayout.LayoutParams(MATCH, MATCH))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(12))
        }
        stack.addView(root, FrameLayout.LayoutParams(MATCH, MATCH))

        // Top bar: back • logo • brand
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(circleButton("‹", 40) { showExitDialog() }.apply { textSize = 24f })
        top.addView(PulseLogo(this), LinearLayout.LayoutParams(dp(38), dp(26)).apply {
            marginStart = dp(14)
            marginEnd = dp(8)
        })
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(brandTitle(17f))
        brand.addView(label("Ultimate Device Health Checker", 10f, Palette.MUTED))
        top.addView(brand, LinearLayout.LayoutParams(0, WRAP, 1f))
        root.addView(top, lpMatchWrap(bottom = dp(10)))

        // Glowing header card: icon • step • title • live subtitle
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = gradientBg(
                if (Palette.dark) intArrayOf(Color.rgb(16, 32, 84), Color.rgb(12, 18, 48), Color.rgb(30, 22, 80))
                else intArrayOf(Color.WHITE, Color.rgb(239, 246, 255), Color.rgb(245, 243, 255)),
                20f, strokeColor = Palette.alpha(Palette.ACCENT, 170),
            )
            elevation = if (Palette.dark) 0f else dpf(3f)
        }
        val icon = intent.getStringExtra(EXTRA_ICON) ?: "🧪"
        header.addView(iconBadge(icon, Palette.CYAN, 58, 28f).apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(14)
        })
        val ht = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        intent.getStringExtra(EXTRA_STEP)?.let { ht.addView(label(it, 10f, Palette.ACCENT, true)) }
        ht.addView(label(testTitle, 21f, Palette.TEXT, true))
        subtitleView = label("Running diagnostics…", 12f, Palette.MUTED).also { ht.addView(it) }
        header.addView(ht, LinearLayout.LayoutParams(0, WRAP, 1f))
        root.addView(header, lpMatchWrap())

        instructionView = label("", 13f, Palette.MUTED).apply {
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = roundedBg(Palette.alpha(Palette.CARD, if (Palette.dark) 170 else 220), dpf(14f), Palette.STROKE, dp(1))
        }.also { root.addView(it, lpMatchWrap(top = dp(8))) }

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(MATCH, 0, 1f).apply {
            topMargin = dp(10)
            bottomMargin = dp(8)
        })

        // Status card with scanning bar
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(12))
            background = roundedBg(Palette.alpha(Palette.CARD, if (Palette.dark) 200 else 240), dpf(16f), Palette.alpha(Palette.ACCENT, 90), dp(1))
        }
        statusView = label("Testing… please wait", 14f, Palette.TEXT, true).apply { gravity = Gravity.CENTER }
        statusCard.addView(statusView, lpMatchWrap(bottom = dp(8)))
        scanBar = ScanBar(this)
        statusCard.addView(scanBar, LinearLayout.LayoutParams(MATCH, dp(6)))
        root.addView(statusCard, lpMatchWrap(bottom = dp(10)))

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val f = actionButton("✕ Fail", Palette.FAIL) { finishWith(TestStatus.FAIL) }
        val s = actionButton("Skip", Palette.CARD_ALT) { finishWith(TestStatus.SKIP) }
        val p = actionButton("✓ Pass", Palette.PASS) { finishWith(TestStatus.PASS) }
        for (b in listOf(f, s, p)) {
            b.cornerRadius = dp(26)
            b.minHeight = dp(52)
        }
        bar.addView(f, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(6) })
        bar.addView(s, LinearLayout.LayoutParams(0, WRAP, 0.8f).apply { marginEnd = dp(6) })
        bar.addView(p, LinearLayout.LayoutParams(0, WRAP, 1f))
        failButton = f
        skipButton = s
        passButton = p
        root.addView(bar, lpMatchWrap())
        setContentView(stack)
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
        val mode = HoloView.modeFor(testId)
        if (mode != HoloView.NONE) {
            val box = FrameLayout(this).apply {
                background = roundedBg(Palette.alpha(Palette.CARD, if (Palette.dark) 150 else 230), dpf(20f), Palette.alpha(Palette.CYAN, 110), dp(1))
                setPadding(dp(10), dp(10), dp(10), dp(10))
            }
            val hv = HoloView(this, mode)
            box.addView(hv, FrameLayout.LayoutParams(MATCH, MATCH))
            col.addView(box, LinearLayout.LayoutParams(MATCH, dp(176)).apply { bottomMargin = dp(10) })
            holo = hv
        }
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
        if (color == Palette.PASS) {
            subtitleView?.text = "Diagnostics complete ✓"
            subtitleView?.setTextColor(Palette.PASS)
        }
    }

    protected fun isTestDone(): Boolean = done || autoPending

    fun finishWith(status: TestStatus, note: String? = currentNote()) {
        if (done) return
        done = true
        ResultStore.save(this, testId, status, note)
        setResult(RESULT_OK, Intent().putExtra(EXTRA_STATUS, status.name))
        if (status == TestStatus.SKIP) {
            finish()
        } else {
            playOutcome(status == TestStatus.PASS) { finish() }
        }
    }

    protected fun autoPass(note: String? = currentNote(), delayMs: Long = 700) {
        if (done || autoPending) return
        autoPending = true
        setStatus("✓ PASSED", Palette.PASS)
        handler.postDelayed({ finishWith(TestStatus.PASS, note) }, delayMs)
    }

    /** Cinematic result overlay: glowing ✓ / ✕ badge, sound and haptic, then [then]. */
    private fun playOutcome(pass: Boolean, then: () -> Unit) {
        val host = findViewById<FrameLayout>(android.R.id.content)
        if (pass) holo?.success() else holo?.fail()
        if (pass) scanBar?.pass() else scanBar?.fail()
        val color = if (pass) Palette.PASS else Palette.FAIL
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Palette.alpha(Palette.BG, 215))
            isClickable = true
            alpha = 0f
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val ring = FrameLayout(this)
        val halo = View(this).apply { background = roundedBg(Palette.alpha(color, 60), dpf(90f)) }
        ring.addView(halo, FrameLayout.LayoutParams(dp(180), dp(180), Gravity.CENTER))
        val badge = label(if (pass) "✓" else "✕", 70f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = gradientBg(
                if (pass) intArrayOf(Palette.PASS, Palette.CYAN) else intArrayOf(Palette.FAIL, Palette.PURPLE),
                70f, GradientDrawable.Orientation.TL_BR,
            )
            elevation = dpf(12f)
        }
        ring.addView(badge, FrameLayout.LayoutParams(dp(140), dp(140), Gravity.CENTER))
        box.addView(ring, LinearLayout.LayoutParams(dp(190), dp(190)))
        box.addView(label(if (pass) "TEST PASSED" else "TEST FAILED", 24f, color, true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
        }, lpMatchWrap(top = dp(12)))
        box.addView(label(testTitle, 14f, Palette.MUTED).apply { gravity = Gravity.CENTER }, lpMatchWrap(top = dp(4)))
        overlay.addView(box, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        host.addView(overlay, FrameLayout.LayoutParams(MATCH, MATCH))

        overlay.animate().alpha(1f).setDuration(180).start()
        badge.scaleX = 0.2f
        badge.scaleY = 0.2f
        badge.animate().scaleX(1f).scaleY(1f).setDuration(480).setInterpolator(OvershootInterpolator(2.4f)).start()
        halo.scaleX = 0.5f
        halo.scaleY = 0.5f
        halo.alpha = 1f
        halo.animate().scaleX(1.25f).scaleY(1.25f).alpha(0.2f).setDuration(900).start()

        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 55)
            tg.startTone(if (pass) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK, 200)
            handler.postDelayed({ tg.release() }, 450)
        } catch (e: Exception) {
            // no audio available
        }
        val fb = if (Build.VERSION.SDK_INT >= 30) {
            if (pass) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        overlay.performHapticFeedback(fb)
        handler.postDelayed({ then() }, 950)
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
