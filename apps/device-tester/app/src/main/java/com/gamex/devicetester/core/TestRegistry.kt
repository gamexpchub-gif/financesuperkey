package com.gamex.devicetester.core

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import androidx.core.os.bundleOf
import com.gamex.devicetester.tests.AllSensorsActivity
import com.gamex.devicetester.tests.BatteryTestActivity
import com.gamex.devicetester.tests.BiometricTestActivity
import com.gamex.devicetester.tests.BluetoothTestActivity
import com.gamex.devicetester.tests.ButtonsTestActivity
import com.gamex.devicetester.tests.CameraTestActivity
import com.gamex.devicetester.tests.DisplayTestActivity
import com.gamex.devicetester.tests.FlashTestActivity
import com.gamex.devicetester.tests.GpsTestActivity
import com.gamex.devicetester.tests.HeadsetTestActivity
import com.gamex.devicetester.tests.ImeiCheckActivity
import com.gamex.devicetester.tests.MicTestActivity
import com.gamex.devicetester.tests.MultiTouchTestActivity
import com.gamex.devicetester.tests.NfcTestActivity
import com.gamex.devicetester.tests.ProximityTestActivity
import com.gamex.devicetester.tests.SecurityCheckActivity
import com.gamex.devicetester.tests.SensorTestActivity
import com.gamex.devicetester.tests.SimNetworkActivity
import com.gamex.devicetester.tests.SpeakerTestActivity
import com.gamex.devicetester.tests.StorageTestActivity
import com.gamex.devicetester.tests.TouchTestActivity
import com.gamex.devicetester.tests.VibrationTestActivity
import com.gamex.devicetester.tests.WifiTestActivity

object TestRegistry {
    const val CAT_DISPLAY = "Display & Touch"
    const val CAT_AUDIO = "Audio & Vibration"
    const val CAT_BUTTONS = "Buttons"
    const val CAT_SENSORS = "Sensors"
    const val CAT_CAMERA = "Cameras"
    const val CAT_BIO = "Biometrics"
    const val CAT_CONN = "Connectivity & SIM"
    const val CAT_POWER = "Battery & Charging"
    const val CAT_SYSTEM = "Storage & Security"

    fun build(c: Context): List<TestItem> {
        val list = mutableListOf<TestItem>()
        fun add(id: String, title: String, cat: String, icon: String, cls: Class<out Activity>, extras: Bundle = Bundle()) {
            list += TestItem(id, title, cat, icon, cls, extras)
        }
        val sm = c.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        fun has(type: Int) = sm.getDefaultSensor(type) != null
        val pm = c.packageManager

        // Display & touch
        add("display", "Display colors / dead pixels", CAT_DISPLAY, "🖥️", DisplayTestActivity::class.java)
        add("touch", "Touch screen (full grid)", CAT_DISPLAY, "👆", TouchTestActivity::class.java)
        add("multitouch", "Multi-touch", CAT_DISPLAY, "🖐️", MultiTouchTestActivity::class.java)

        // Audio
        add("speaker_loud", "Loudspeaker", CAT_AUDIO, "🔊", SpeakerTestActivity::class.java,
            bundleOf(SpeakerTestActivity.EXTRA_MODE to SpeakerTestActivity.MODE_LOUD))
        add("speaker_ear", "Earpiece (call speaker)", CAT_AUDIO, "📞", SpeakerTestActivity::class.java,
            bundleOf(SpeakerTestActivity.EXTRA_MODE to SpeakerTestActivity.MODE_EAR))
        add("mic_main", "Microphone — main (bottom)", CAT_AUDIO, "🎙️", MicTestActivity::class.java,
            bundleOf(MicTestActivity.EXTRA_MODE to MicTestActivity.MODE_MAIN))
        add("mic_secondary", "Microphone — secondary (top / noise-cancel)", CAT_AUDIO, "🎤", MicTestActivity::class.java,
            bundleOf(MicTestActivity.EXTRA_MODE to MicTestActivity.MODE_SECONDARY))
        add("headset", "Earphone jack / USB headset", CAT_AUDIO, "🎧", HeadsetTestActivity::class.java)
        add("vibration", "Vibration motor", CAT_AUDIO, "📳", VibrationTestActivity::class.java)

        // Buttons
        add("buttons", "Volume & power buttons", CAT_BUTTONS, "🔘", ButtonsTestActivity::class.java)

        // Sensors
        add("proximity", "Proximity sensor", CAT_SENSORS, "📡", ProximityTestActivity::class.java)
        add("light", "Light sensor (auto-brightness)", CAT_SENSORS, "💡", SensorTestActivity::class.java,
            bundleOf(SensorTestActivity.EXTRA_TYPE to Sensor.TYPE_LIGHT))
        add("accel", "Accelerometer (auto-rotate)", CAT_SENSORS, "📐", SensorTestActivity::class.java,
            bundleOf(SensorTestActivity.EXTRA_TYPE to Sensor.TYPE_ACCELEROMETER))
        if (has(Sensor.TYPE_GYROSCOPE)) {
            add("gyro", "Gyroscope", CAT_SENSORS, "🌀", SensorTestActivity::class.java,
                bundleOf(SensorTestActivity.EXTRA_TYPE to Sensor.TYPE_GYROSCOPE))
        }
        add("compass", "Magnetometer / compass", CAT_SENSORS, "🧭", SensorTestActivity::class.java,
            bundleOf(SensorTestActivity.EXTRA_TYPE to Sensor.TYPE_MAGNETIC_FIELD))
        if (has(Sensor.TYPE_PRESSURE)) {
            add("barometer", "Barometer", CAT_SENSORS, "🌡️", SensorTestActivity::class.java,
                bundleOf(SensorTestActivity.EXTRA_TYPE to Sensor.TYPE_PRESSURE))
        }
        if (has(Sensor.TYPE_STEP_COUNTER)) {
            add("steps", "Step counter", CAT_SENSORS, "🚶", SensorTestActivity::class.java,
                bundleOf(SensorTestActivity.EXTRA_TYPE to Sensor.TYPE_STEP_COUNTER))
        }
        add("all_sensors", "All sensors (live list)", CAT_SENSORS, "🧪", AllSensorsActivity::class.java)

        // Cameras — one test per camera the system exposes.
        val cm = c.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val ids = try {
            cm.cameraIdList.toList()
        } catch (e: Exception) {
            emptyList()
        }
        var backN = 0
        var frontN = 0
        var extN = 0
        for (id in ids) {
            val ch = try {
                cm.getCameraCharacteristics(id)
            } catch (e: Exception) {
                continue
            }
            val px = ch.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            val mp = if (px != null) px.width.toLong() * px.height / 1_000_000.0 else 0.0
            val name = when (ch.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_FRONT -> "Front camera" + if (++frontN > 1) " $frontN" else ""
                CameraCharacteristics.LENS_FACING_BACK -> "Back camera" + if (++backN > 1) " $backN" else ""
                else -> "External camera" + if (++extN > 1) " $extN" else ""
            }
            val mpText = if (mp > 0) String.format(" — %.0f MP", mp) else ""
            add("camera_$id", "$name$mpText", CAT_CAMERA, "📷", CameraTestActivity::class.java,
                bundleOf(CameraTestActivity.EXTRA_CAMERA_ID to id))
        }
        if (ids.isEmpty()) {
            add("camera_0", "Back camera", CAT_CAMERA, "📷", CameraTestActivity::class.java,
                bundleOf(CameraTestActivity.EXTRA_CAMERA_ID to "0"))
        }
        add("flash", "Flashlight / torch", CAT_CAMERA, "🔦", FlashTestActivity::class.java)

        // Biometrics
        add("fingerprint", "Fingerprint sensor", CAT_BIO, "☝️", BiometricTestActivity::class.java,
            bundleOf(BiometricTestActivity.EXTRA_MODE to BiometricTestActivity.MODE_FINGER))
        add("face", "Face unlock", CAT_BIO, "🙂", BiometricTestActivity::class.java,
            bundleOf(BiometricTestActivity.EXTRA_MODE to BiometricTestActivity.MODE_FACE))

        // Connectivity
        add("sim", "SIM card & mobile network", CAT_CONN, "📶", SimNetworkActivity::class.java)
        add("imei", "IMEI validity & blacklist check", CAT_CONN, "🛡️", ImeiCheckActivity::class.java)
        add("wifi", "Wi-Fi & internet", CAT_CONN, "🛜", WifiTestActivity::class.java)
        add("bluetooth", "Bluetooth", CAT_CONN, "🔷", BluetoothTestActivity::class.java)
        add("gps", "GPS / GNSS satellites", CAT_CONN, "🛰️", GpsTestActivity::class.java)
        if (pm.hasSystemFeature(PackageManager.FEATURE_NFC)) {
            add("nfc", "NFC", CAT_CONN, "💳", NfcTestActivity::class.java)
        }

        // Power
        add("battery", "Battery health & charging port", CAT_POWER, "🔋", BatteryTestActivity::class.java)

        // System
        add("storage", "Storage speed, RAM & real specs", CAT_SYSTEM, "💾", StorageTestActivity::class.java)
        add("security", "Root, bootloader & lock check", CAT_SYSTEM, "🔐", SecurityCheckActivity::class.java)

        return list
    }
}
