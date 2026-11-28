package com.gamex.devicetester.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Display
import com.gamex.devicetester.ui.formatBytes
import java.io.File
import kotlin.math.sqrt

/** Collects a full hardware / software profile of the phone. */
object DeviceInfo {

    fun collect(c: Context): LinkedHashMap<String, List<Pair<String, String>>> {
        val out = LinkedHashMap<String, List<Pair<String, String>>>()

        out["Device"] = listOf(
            "Manufacturer" to Build.MANUFACTURER,
            "Brand" to Build.BRAND,
            "Model" to Build.MODEL,
            "Device / codename" to Build.DEVICE,
            "Product" to Build.PRODUCT,
            "Board" to Build.BOARD,
            "Hardware" to Build.HARDWARE,
        )

        val sw = mutableListOf(
            "Android version" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Security patch" to (if (Build.VERSION.SDK_INT >= 23) Build.VERSION.SECURITY_PATCH else "n/a"),
            "Build number" to Build.DISPLAY,
            "Kernel" to (System.getProperty("os.version") ?: "unknown"),
            "Baseband" to (Build.getRadioVersion() ?: "unknown"),
            "Bootloader" to Build.BOOTLOADER,
            "Build type / tags" to "${Build.TYPE} / ${Build.TAGS}",
            "Fingerprint" to Build.FINGERPRINT,
            "Uptime" to formatUptime(SystemClock.elapsedRealtime()),
        )
        out["Software"] = sw

        val hw = mutableListOf<Pair<String, String>>()
        if (Build.VERSION.SDK_INT >= 31) hw += "SoC" to "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}"
        hw += "CPU ABIs" to Build.SUPPORTED_ABIS.joinToString(", ")
        hw += "CPU cores" to Runtime.getRuntime().availableProcessors().toString()
        maxCpuFreqGhz()?.let { hw += "Max CPU clock" to String.format("%.2f GHz", it) }
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        hw += "OpenGL ES" to (am.deviceConfigurationInfo?.glEsVersion ?: "unknown")
        out["Hardware"] = hw

        out["Display"] = displayInfo(c)

        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        out["Memory & storage"] = listOf(
            "RAM (real)" to "${formatBytes(mem.totalMem)}  ≈ ${marketingRam(mem.totalMem)} GB class",
            "RAM available" to formatBytes(mem.availMem),
            "Internal storage (real)" to "${formatBytes(total)}  ≈ ${marketingStorage(total)} GB class",
            "Internal storage free" to formatBytes(stat.availableBytes),
            "SD card" to if (hasSdCard(c)) "Present" else "Not detected",
        )

        val sm = c.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val cm = c.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val camIds = try {
            cm.cameraIdList.toList()
        } catch (e: Exception) {
            emptyList()
        }
        val camDesc = camIds.joinToString("\n") { id -> CameraSpecs.oneLine(c, id) }
        out["Cameras & sensors"] = listOf(
            "Cameras exposed" to camIds.size.toString(),
            "Camera list" to camDesc.ifEmpty { "none" },
            "Sensors" to sm.getSensorList(Sensor.TYPE_ALL).size.toString(),
        )

        val batt = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val design = designCapacityMah(c)
        out["Battery"] = listOf(
            "Design capacity" to if (design > 0) "${design.toInt()} mAh" else "unknown",
            "Technology" to (batt?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "unknown"),
        )
        return out
    }

    fun shortSummary(c: Context): String {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        val stat = StatFs(Environment.getDataDirectory().path)
        return "${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE}\n" +
            "RAM ${marketingRam(mem.totalMem)} GB • Storage ${marketingStorage(stat.totalBytes)} GB"
    }

    private fun displayInfo(c: Context): List<Pair<String, String>> {
        val dmgr = c.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val d = dmgr.getDisplay(Display.DEFAULT_DISPLAY) ?: return listOf("Display" to "unknown")
        val m = DisplayMetrics()
        @Suppress("DEPRECATION")
        d.getRealMetrics(m)
        val wIn = m.widthPixels / m.xdpi
        val hIn = m.heightPixels / m.ydpi
        val inches = sqrt((wIn * wIn + hIn * hIn).toDouble())
        val list = mutableListOf(
            "Resolution" to "${m.widthPixels} × ${m.heightPixels}",
            "Density" to "${m.densityDpi} dpi",
            "Size (approx.)" to String.format("%.1f\"", inches),
            "Refresh rate (current)" to String.format("%.0f Hz", d.refreshRate),
        )
        val maxHz = d.supportedModes.maxOfOrNull { it.refreshRate } ?: d.refreshRate
        list += "Refresh rate (max)" to String.format("%.0f Hz", maxHz)
        val hdr = d.hdrCapabilities?.supportedHdrTypes?.isNotEmpty() == true
        list += "HDR" to if (hdr) "Supported" else "No"
        return list
    }

    private fun hasSdCard(c: Context): Boolean = try {
        c.getExternalFilesDirs(null).count { it != null } > 1
    } catch (e: Exception) {
        false
    }

    fun maxCpuFreqGhz(): Double? {
        var maxKhz = 0L
        for (i in 0 until Runtime.getRuntime().availableProcessors()) {
            try {
                val f = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
                if (f.exists()) {
                    val v = f.readText().trim().toLongOrNull() ?: 0L
                    if (v > maxKhz) maxKhz = v
                }
            } catch (e: Exception) {
                // not readable on this device
            }
        }
        return if (maxKhz > 0) maxKhz / 1_000_000.0 else null
    }

    fun designCapacityMah(c: Context): Double = try {
        val cls = Class.forName("com.android.internal.os.PowerProfile")
        val obj = cls.getConstructor(Context::class.java).newInstance(c)
        (cls.getMethod("getBatteryCapacity").invoke(obj) as? Double) ?: 0.0
    } catch (e: Throwable) {
        0.0
    }

    /** Round real RAM up to the advertised size class (e.g. 7.4 GiB → 8). */
    fun marketingRam(bytes: Long): Int {
        val gib = bytes / (1024.0 * 1024.0 * 1024.0)
        val classes = intArrayOf(1, 2, 3, 4, 6, 8, 10, 12, 16, 18, 20, 24, 32)
        return classes.firstOrNull { it >= gib } ?: kotlin.math.ceil(gib).toInt()
    }

    /** Round real storage to the advertised size class (e.g. 110 GB → 128). */
    fun marketingStorage(bytes: Long): Int {
        val gb = bytes / 1_000_000_000.0
        var size = 8
        while (size < gb && size < 4096) size *= 2
        return size
    }

    private fun formatUptime(ms: Long): String {
        val h = ms / 3_600_000
        val m = (ms / 60_000) % 60
        return "${h}h ${m}m"
    }
}
