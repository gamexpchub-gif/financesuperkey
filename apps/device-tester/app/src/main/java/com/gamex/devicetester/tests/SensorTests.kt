package com.gamex.devicetester.tests

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.gamex.devicetester.core.BaseTestActivity
import com.gamex.devicetester.ui.KeyValueList
import com.gamex.devicetester.ui.MATCH
import com.gamex.devicetester.ui.Palette
import com.gamex.devicetester.ui.card
import com.gamex.devicetester.ui.dp
import com.gamex.devicetester.ui.dpf
import com.gamex.devicetester.ui.label
import com.gamex.devicetester.ui.lpMatchWrap
import com.gamex.devicetester.ui.roundedBg
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Volume up / down and power (screen off → on) must all be detected. */
class ButtonsTestActivity : BaseTestActivity() {
    private val seen = mutableSetOf<String>()
    private val rows = LinkedHashMap<String, TextView>()
    private var screenWentOff = false
    private val extras = mutableListOf<String>()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> screenWentOff = true
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> if (screenWentOff) mark("power")
            }
        }
    }

    override fun currentNote(): String =
        "Detected: " + seen.joinToString(", ") + if (extras.isNotEmpty()) " • extra keys: " + extras.joinToString(", ") else ""

    override fun onCreateTest(savedInstanceState: Bundle?) {
        setInstruction("Press VOLUME UP, then VOLUME DOWN. Then press POWER to turn the screen off and press it again to turn it back on.")
        val col = scrollColumn()
        for ((key, title) in listOf("up" to "Volume Up", "down" to "Volume Down", "power" to "Power (off → on)")) {
            val row = label("⬜  $title", 20f, Palette.TEXT, true).apply {
                setPadding(dp(16), dp(16), dp(16), dp(16))
                background = roundedBg(Palette.CARD, dpf(14f), Palette.STROKE, dp(1))
            }
            col.addView(row, lpMatchWrap(bottom = dp(10)))
            rows[key] = row
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun mark(key: String) {
        if (!seen.add(key)) return
        rows[key]?.let {
            it.text = it.text.toString().replace("⬜", "✅")
            it.background = roundedBg(Palette.PASS, dpf(14f))
        }
        if (seen.containsAll(listOf("up", "down", "power"))) autoPass()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> { mark("up"); return true }
            KeyEvent.KEYCODE_VOLUME_DOWN -> { mark("down"); return true }
            KeyEvent.KEYCODE_CAMERA, KeyEvent.KEYCODE_FOCUS, KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_ASSIST -> {
                val name = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
                if (name !in extras) extras += name
                setStatus("Extra key detected: $name", Palette.ACCENT)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            // not registered
        }
        super.onDestroy()
    }
}

/** Cover / uncover the top of the phone; needs far → near → far. */
class ProximityTestActivity : BaseTestActivity(), SensorEventListener {
    private lateinit var sm: SensorManager
    private var sensor: Sensor? = null
    private lateinit var big: TextView
    private lateinit var values: TextView
    private var sawNear = false
    private var transitions = 0
    private var lastNear: Boolean? = null

    override fun currentNote(): String = "Near/far transitions: $transitions"

    override fun onCreateTest(savedInstanceState: Bundle?) {
        sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensor = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        setInstruction("Cover the top of the phone (near the earpiece / front camera) with your hand, then remove it. This sensor turns the screen off during calls.")
        val col = scrollColumn()
        big = label("FAR", 44f, Palette.TEXT, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(40), 0, dp(40))
            background = roundedBg(Palette.CARD, dpf(20f), Palette.STROKE, dp(1))
        }
        col.addView(big, lpMatchWrap(bottom = dp(12)))
        values = label("", 13f, Palette.MUTED)
        col.addView(values)
        val s = sensor
        if (s == null) {
            big.text = "NOT FOUND"
            setStatus("No proximity sensor reported — screen may stay on during calls", Palette.FAIL)
        } else {
            values.text = "${s.name} • ${s.vendor} • range ${s.maximumRange}"
        }
    }

    override fun onResume() {
        super.onResume()
        sensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        sm.unregisterListener(this)
    }

    override fun onSensorChanged(e: SensorEvent) {
        val s = sensor ?: return
        val v = e.values[0]
        val near = v < s.maximumRange && v < 5f || v == 0f
        big.text = if (near) "NEAR" else "FAR"
        big.background = roundedBg(if (near) Palette.ACCENT else Palette.CARD, dpf(20f), Palette.STROKE, dp(1))
        values.text = "${s.name}\nValue: $v (max ${s.maximumRange})"
        if (lastNear != null && lastNear != near) transitions++
        lastNear = near
        if (near) sawNear = true
        if (sawNear && !near && transitions >= 2) autoPass()
        else if (!sawNear) setStatus("Now cover the sensor", Palette.TEXT)
        else if (near) setStatus("Detected NEAR — now remove your hand", Palette.ACCENT)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

/** Generic live sensor test with an objective pass rule per sensor type. */
class SensorTestActivity : BaseTestActivity(), SensorEventListener {
    companion object {
        const val EXTRA_TYPE = "sensor_type"
    }

    private lateinit var sm: SensorManager
    private var sensor: Sensor? = null
    private var accelForCompass: Sensor? = null
    private var type = Sensor.TYPE_ACCELEROMETER
    private lateinit var big: TextView
    private lateinit var info: TextView
    private val bars = mutableListOf<ProgressBar>()
    private var mins = FloatArray(3) { Float.MAX_VALUE }
    private var maxs = FloatArray(3) { -Float.MAX_VALUE }
    private var events = 0
    private var firstValue = Float.NaN
    private var lastUi = 0L
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var haveGravity = false
    private var summary = ""

    override fun currentNote(): String = summary

    override fun onCreateTest(savedInstanceState: Bundle?) {
        sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        type = intent.getIntExtra(EXTRA_TYPE, Sensor.TYPE_ACCELEROMETER)
        sensor = sm.getDefaultSensor(type)
        if (type == Sensor.TYPE_MAGNETIC_FIELD) accelForCompass = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        setInstruction(
            when (type) {
                Sensor.TYPE_ACCELEROMETER -> "Rotate the phone in all directions (portrait, landscape, face up/down). Passes when every axis responds."
                Sensor.TYPE_GYROSCOPE -> "Twist / rotate the phone quickly in your hand."
                Sensor.TYPE_MAGNETIC_FIELD -> "Rotate the phone slowly / wave it in a figure-8. The compass heading should follow."
                Sensor.TYPE_LIGHT -> "Cover the top of the phone with your hand, then point it at a lamp or window."
                Sensor.TYPE_PRESSURE -> "Just wait — a valid air pressure reading (300–1100 hPa) passes."
                Sensor.TYPE_STEP_COUNTER -> "Walk 10 or more steps holding the phone."
                else -> "Move the phone and watch the values change."
            },
        )

        val col = scrollColumn()
        val c = card()
        big = label("—", 30f, Palette.TEXT, true).apply { gravity = Gravity.CENTER }
        c.addView(big, lpMatchWrap())
        col.addView(c, lpMatchWrap(bottom = dp(12)))
        val axes = if (type == Sensor.TYPE_LIGHT || type == Sensor.TYPE_PRESSURE || type == Sensor.TYPE_STEP_COUNTER) 1 else 3
        for (i in 0 until axes) {
            val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
            col.addView(pb, LinearLayout.LayoutParams(MATCH, dp(18)).apply { bottomMargin = dp(6) })
            bars += pb
        }
        info = label("", 12f, Palette.MUTED)
        col.addView(info, lpMatchWrap(top = dp(8)))

        val s = sensor
        if (s == null) {
            big.text = "NOT FOUND"
            setStatus("This sensor is not present on the device", Palette.FAIL)
            return
        }
        info.text = "${s.name}\nVendor: ${s.vendor} • v${s.version}\nRange: ${s.maximumRange} • Resolution: ${s.resolution}\nPower: ${s.power} mA"
        if (type == Sensor.TYPE_STEP_COUNTER && Build.VERSION.SDK_INT >= 29) {
            withPermissions(Manifest.permission.ACTIVITY_RECOGNITION) { ok ->
                if (!ok) setStatus("Physical-activity permission denied — step counter blocked", Palette.FAIL)
                else register()
            }
        }
    }

    private fun register() {
        sensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        accelForCompass?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onResume() {
        super.onResume()
        if (type != Sensor.TYPE_STEP_COUNTER || Build.VERSION.SDK_INT < 29 ||
            hasPermission(Manifest.permission.ACTIVITY_RECOGNITION)
        ) register()
    }

    override fun onPause() {
        super.onPause()
        sm.unregisterListener(this)
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (e.sensor.type == Sensor.TYPE_ACCELEROMETER && type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(e.values, 0, gravity, 0, 3)
            haveGravity = true
            return
        }
        if (e.sensor.type != type) return
        events++
        val n = minOf(3, e.values.size)
        for (i in 0 until n) {
            mins[i] = minOf(mins[i], e.values[i])
            maxs[i] = maxOf(maxs[i], e.values[i])
        }
        if (firstValue.isNaN()) firstValue = e.values[0]

        val now = SystemClock.elapsedRealtime()
        if (now - lastUi < 80) return
        lastUi = now
        updateUi(e)
        evaluate(e)
    }

    private fun updateUi(e: SensorEvent) {
        val s = sensor ?: return
        when (type) {
            Sensor.TYPE_LIGHT -> big.text = String.format("%.0f lux", e.values[0])
            Sensor.TYPE_PRESSURE -> big.text = String.format("%.1f hPa", e.values[0])
            Sensor.TYPE_STEP_COUNTER -> big.text = "${(e.values[0] - firstValue).roundToInt()} steps"
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(e.values, 0, geomagnetic, 0, 3)
                var heading = ""
                if (haveGravity) {
                    val r = FloatArray(9)
                    if (SensorManager.getRotationMatrix(r, null, gravity, geomagnetic)) {
                        val o = FloatArray(3)
                        SensorManager.getOrientation(r, o)
                        val deg = (Math.toDegrees(o[0].toDouble()) + 360) % 360
                        heading = String.format("\n🧭 %.0f° %s", deg, compassDir(deg))
                    }
                }
                big.text = String.format("X %.1f  Y %.1f  Z %.1f µT%s", e.values[0], e.values[1], e.values[2], heading)
            }
            else -> big.text = String.format("X %.2f\nY %.2f\nZ %.2f", e.values[0], e.values[1], e.values[2])
        }
        val range = if (s.maximumRange > 0) s.maximumRange else 1f
        for (i in bars.indices) {
            if (i < e.values.size) {
                val v = e.values[i]
                bars[i].progress = if (bars.size == 1) (v / range * 100).roundToInt().coerceIn(0, 100)
                else ((v / range + 1f) * 50).roundToInt().coerceIn(0, 100)
            }
        }
    }

    private fun span(i: Int) = if (maxs[i] >= mins[i]) maxs[i] - mins[i] else 0f

    private fun evaluate(e: SensorEvent) {
        val ok: Boolean
        when (type) {
            Sensor.TYPE_ACCELEROMETER -> {
                ok = span(0) > 8f && span(1) > 8f && span(2) > 8f
                summary = String.format("Axis span X %.1f Y %.1f Z %.1f m/s²", span(0), span(1), span(2))
            }
            Sensor.TYPE_GYROSCOPE -> {
                val peak = (0..2).maxOf { max(abs(mins[it]), abs(maxs[it])) }
                ok = peak > 2f
                summary = String.format("Peak rotation %.1f rad/s", peak)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                val sp = (0..2).maxOf { span(it) }
                ok = sp > 30f
                summary = String.format("Field change %.0f µT", sp)
            }
            Sensor.TYPE_LIGHT -> {
                ok = span(0) >= 25f
                summary = String.format("Lux range %.0f – %.0f", mins[0], maxs[0])
            }
            Sensor.TYPE_PRESSURE -> {
                ok = events >= 3 && e.values[0] in 300f..1100f
                summary = String.format("Pressure %.1f hPa", e.values[0])
            }
            Sensor.TYPE_STEP_COUNTER -> {
                val steps = e.values[0] - firstValue
                ok = steps >= 5
                summary = "Counted ${steps.roundToInt()} steps"
            }
            else -> {
                ok = (0..2).any { span(it) > 0f }
                summary = "Values changing"
            }
        }
        if (ok) autoPass(summary) else setStatus(summary, Palette.MUTED)
    }

    private fun compassDir(deg: Double): String {
        val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return dirs[((deg + 22.5) / 45).toInt() % 8]
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (type == Sensor.TYPE_MAGNETIC_FIELD && sensor?.type == type) {
            val txt = when (accuracy) {
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "Calibration: high"
                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Calibration: medium"
                SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Calibration: low — wave in figure-8"
                else -> "Calibration: unreliable — wave in figure-8"
            }
            setInstruction("Rotate the phone slowly / wave it in a figure-8.  $txt")
        }
    }
}

/** Every sensor the phone exposes, with live values. */
class AllSensorsActivity : BaseTestActivity(), SensorEventListener {
    private lateinit var sm: SensorManager
    private val views = HashMap<Sensor, TextView>()
    private val lastUpdate = HashMap<Sensor, Long>()
    private val responded = mutableSetOf<Sensor>()
    private var sensors: List<Sensor> = emptyList()

    override fun currentNote(): String = "${sensors.size} sensors listed, ${responded.size} reporting data"

    override fun onCreateTest(savedInstanceState: Bundle?) {
        sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensors = sm.getSensorList(Sensor.TYPE_ALL)
        setInstruction("All ${sensors.size} sensors reported by the system. Move the phone — values should update. Sensors that only report on events (step, significant motion) may stay idle.")
        val col = scrollColumn()
        for (s in sensors) {
            val c = card()
            c.addView(label(s.name, 14f, Palette.TEXT, true))
            c.addView(label("${s.stringType.substringAfterLast('.')} • ${s.vendor}", 11f, Palette.MUTED))
            val v = label("waiting…", 13f, Palette.MUTED)
            c.addView(v)
            views[s] = v
            col.addView(c, lpMatchWrap(bottom = dp(8)))
        }
    }

    override fun onResume() {
        super.onResume()
        for (s in sensors) {
            if (s.reportingMode != Sensor.REPORTING_MODE_ONE_SHOT) {
                sm.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        sm.unregisterListener(this)
    }

    override fun onSensorChanged(e: SensorEvent) {
        val now = SystemClock.elapsedRealtime()
        if (now - (lastUpdate[e.sensor] ?: 0L) < 250) return
        lastUpdate[e.sensor] = now
        val first = responded.add(e.sensor)
        views[e.sensor]?.let { tv ->
            tv.text = e.values.take(6).joinToString("   ") { String.format("%.2f", it) }
            tv.setTextColor(Palette.PASS)
        }
        if (first) setStatus("${responded.size} / ${sensors.size} sensors reporting", Palette.TEXT)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
