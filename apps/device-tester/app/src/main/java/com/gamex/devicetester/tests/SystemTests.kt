package com.gamex.devicetester.tests

import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.gamex.devicetester.core.BaseTestActivity
import com.gamex.devicetester.core.DeviceInfo
import com.gamex.devicetester.ui.KeyValueList
import com.gamex.devicetester.ui.Palette
import com.gamex.devicetester.ui.actionButton
import com.gamex.devicetester.ui.card
import com.gamex.devicetester.ui.dp
import com.gamex.devicetester.ui.formatBytes
import com.gamex.devicetester.ui.label
import com.gamex.devicetester.ui.lpMatchWrap
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.CRC32
import kotlin.math.abs
import kotlin.random.Random

/** Battery health, temperature, voltage, estimated capacity and charging-port detection. */
class BatteryTestActivity : BaseTestActivity() {
    private lateinit var kv: KeyValueList
    private lateinit var bm: BatteryManager
    private var design = 0.0
    private var note = ""
    private var healthGood = true

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) update(intent)
        }
    }

    override fun currentNote(): String = note

    override fun onCreateTest(savedInstanceState: Bundle?) {
        bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        design = DeviceInfo.designCapacityMah(this)
        setInstruction("Connect the charger / USB cable. The app checks the charging port, charging current, battery health, temperature and estimated real capacity.")
        val col = scrollColumn()
        val c = card()
        kv = KeyValueList(this)
        c.addView(kv)
        col.addView(c, lpMatchWrap())
        ContextCompat.registerReceiver(this, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_EXPORTED)
            ?.let { update(it) }
    }

    private fun update(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val health = i.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0
        val volt = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val tech = i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "?"
        val cycles = i.getIntExtra("android.os.extra.CYCLE_COUNT", -1)

        kv.set("Level", "$pct %")
        holo?.battery = pct
        val plugText = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            0 -> "Not plugged"
            else -> "Dock / other"
        }
        kv.set("Charger", plugText, if (plugged != 0) Palette.PASS else Palette.MUTED)
        kv.set("Status", when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            else -> "Unknown"
        })
        val healthText = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }
        healthGood = health == BatteryManager.BATTERY_HEALTH_GOOD || health == BatteryManager.BATTERY_HEALTH_UNKNOWN
        kv.set("Health (system)", healthText, if (healthGood) Palette.PASS else Palette.FAIL)
        kv.set("Temperature", String.format("%.1f °C", temp), if (temp > 45) Palette.FAIL else Palette.TEXT)
        kv.set("Voltage", "$volt mV")
        kv.set("Technology", tech)
        if (cycles >= 0) kv.set("Charge cycles", cycles.toString())

        var current = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (current != Int.MIN_VALUE && current != 0) {
            if (abs(current) > 20_000) current /= 1000 // µA → mA
            kv.set("Current now", "$current mA")
        }
        if (design > 0) kv.set("Design capacity", "${design.toInt()} mAh")
        val counter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        var healthPct = -1
        if (counter > 0 && pct >= 10 && design > 0) {
            val est = counter / 1000.0 / (pct / 100.0)
            healthPct = (est / design * 100).toInt()
            if (healthPct in 30..130) {
                kv.set("Estimated capacity", String.format("%.0f mAh (~%d%%)", est, healthPct.coerceAtMost(100)),
                    if (healthPct >= 80) Palette.PASS else Palette.SKIP)
            } else {
                healthPct = -1
            }
        }

        note = "Health $healthText, ${String.format("%.1f", temp)}°C, ${volt}mV" +
            (if (healthPct > 0) ", est. capacity ~${healthPct.coerceAtMost(100)}%" else "") +
            (if (plugged != 0) ", charging via $plugText" else "")

        if (plugged != 0 && healthGood && temp < 50) {
            autoPass(note, 3000)
        } else if (!healthGood) {
            setStatus("⚠ Battery health reported as $healthText", Palette.FAIL)
        } else if (plugged == 0) {
            setStatus("Plug in the charger to test the charging port", Palette.MUTED)
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
            // ignore
        }
        super.onDestroy()
    }
}

/** Real RAM / storage (catches fake-spec clones) + storage write/read speed & integrity test. */
class StorageTestActivity : BaseTestActivity() {
    private lateinit var kv: KeyValueList
    private var note = ""

    override fun currentNote(): String = note

    override fun onCreateTest(savedInstanceState: Bundle?) {
        setInstruction("Compare the REAL RAM and storage below with the box / advertised specs — clone phones often fake them. Then run the speed & integrity test.")
        val col = scrollColumn()
        val c = card()
        kv = KeyValueList(this)
        c.addView(kv)
        col.addView(c, lpMatchWrap(bottom = dp(12)))

        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        val st = StatFs(Environment.getDataDirectory().path)
        kv.set("RAM (real)", "${formatBytes(mem.totalMem)} → ${DeviceInfo.marketingRam(mem.totalMem)} GB class", Palette.ACCENT)
        kv.set("RAM free", formatBytes(mem.availMem))
        kv.set("Storage (real)", "${formatBytes(st.totalBytes)} → ${DeviceInfo.marketingStorage(st.totalBytes)} GB class", Palette.ACCENT)
        kv.set("Storage free", formatBytes(st.availableBytes))
        DeviceInfo.maxCpuFreqGhz()?.let { kv.set("CPU", String.format("%d cores, up to %.2f GHz", Runtime.getRuntime().availableProcessors(), it)) }

        col.addView(actionButton("⚡ Run speed & integrity test") { v ->
            v.isEnabled = false
            runSpeedTest(st.availableBytes) { v.isEnabled = true }
        }, lpMatchWrap())
    }

    private fun runSpeedTest(free: Long, done: () -> Unit) {
        val sizeMb = if (free > 600L * 1024 * 1024) 128 else if (free > 200L * 1024 * 1024) 32 else 0
        if (sizeMb == 0) {
            setStatus("Not enough free space for the test", Palette.SKIP)
            done()
            return
        }
        setStatus("Writing $sizeMb MB…", Palette.MUTED)
        Thread {
            val file = File(cacheDir, "gx_speedtest.bin")
            val block = ByteArray(1024 * 1024).also { Random(42).nextBytes(it) }
            var writeMbps = 0.0
            var readMbps = 0.0
            var ok = false
            try {
                val wCrc = CRC32()
                val t0 = SystemClock.elapsedRealtimeNanos()
                FileOutputStream(file).use { out ->
                    for (i in 0 until sizeMb) {
                        block[0] = i.toByte()
                        out.write(block)
                        wCrc.update(block)
                    }
                    out.fd.sync()
                }
                val t1 = SystemClock.elapsedRealtimeNanos()
                writeMbps = sizeMb / ((t1 - t0) / 1e9)
                runOnUiThread { setStatus("Reading $sizeMb MB…", Palette.MUTED) }

                val rCrc = CRC32()
                val buf = ByteArray(1024 * 1024)
                val t2 = SystemClock.elapsedRealtimeNanos()
                FileInputStream(file).use { inp ->
                    while (true) {
                        val n = inp.read(buf)
                        if (n <= 0) break
                        rCrc.update(buf, 0, n)
                    }
                }
                val t3 = SystemClock.elapsedRealtimeNanos()
                readMbps = sizeMb / ((t3 - t2) / 1e9)
                ok = rCrc.value == wCrc.value
            } catch (e: Exception) {
                ok = false
            } finally {
                file.delete()
            }
            runOnUiThread {
                done()
                kv.set("Write speed", String.format("%.0f MB/s", writeMbps))
                kv.set("Read speed (cached)", String.format("%.0f MB/s", readMbps))
                kv.set("Data integrity", if (ok) "OK (CRC match)" else "CORRUPTED", if (ok) Palette.PASS else Palette.FAIL)
                note = String.format("RAM %d GB, storage %d GB, write %.0f MB/s, integrity %s",
                    DeviceInfo.marketingRam((getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).let {
                        val m = ActivityManager.MemoryInfo(); it.getMemoryInfo(m); m.totalMem
                    }),
                    DeviceInfo.marketingStorage(StatFs(Environment.getDataDirectory().path).totalBytes),
                    writeMbps, if (ok) "OK" else "FAILED")
                if (ok) autoPass(note, 2500) else autoFail("Storage data corruption detected")
            }
        }.start()
    }
}

/** Root, bootloader, test-keys, debugging, screen lock, encryption, MDM / finance-lock apps. */
class SecurityCheckActivity : BaseTestActivity() {
    private val critical = mutableListOf<String>()
    private val warnings = mutableListOf<String>()

    override fun currentNote(): String =
        if (critical.isEmpty() && warnings.isEmpty()) "No security issues"
        else (critical + warnings).joinToString("; ")

    override fun onCreateTest(savedInstanceState: Bundle?) {
        setInstruction("Automatic check for root, unlocked bootloader, custom ROM, debugging, missing screen lock and device-admin / finance-lock apps. Important when buying a used phone.")
        val col = scrollColumn()
        val c = card()
        val kv = KeyValueList(this)
        c.addView(kv)
        col.addView(c, lpMatchWrap(bottom = dp(12)))

        val rooted = suFound()
        kv.set("Root (su / Magisk)", if (rooted) "DETECTED" else "Not found", if (rooted) Palette.FAIL else Palette.PASS)
        if (rooted) critical += "Rooted"

        val testKeys = Build.TAGS?.contains("test-keys") == true
        kv.set("Firmware signature", if (testKeys) "test-keys (custom ROM)" else "release-keys", if (testKeys) Palette.FAIL else Palette.PASS)
        if (testKeys) critical += "Custom/test-keys firmware"

        val vbState = getProp("ro.boot.verifiedbootstate")
        val flashLocked = getProp("ro.boot.flash.locked")
        val vbmeta = getProp("ro.boot.vbmeta.device_state")
        val unlocked = vbState == "orange" || flashLocked == "0" || vbmeta == "unlocked"
        val bootText = when {
            unlocked -> "UNLOCKED"
            vbState == "yellow" -> "Custom key (yellow)"
            vbState == "green" || flashLocked == "1" || vbmeta == "locked" -> "Locked (verified)"
            else -> "Unknown"
        }
        kv.set("Bootloader", bootText, if (unlocked) Palette.FAIL else if (bootText.startsWith("Locked")) Palette.PASS else Palette.MUTED)
        if (unlocked) critical += "Bootloader unlocked"

        val emulator = Build.FINGERPRINT.startsWith("generic") || Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") || Build.MODEL.contains("Emulator") || Build.PRODUCT.contains("sdk")
        kv.set("Real device", if (emulator) "EMULATOR?" else "Yes", if (emulator) Palette.FAIL else Palette.PASS)
        if (emulator) critical += "Emulator / generic build"

        val dev = Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        val adb = Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        kv.set("Developer options", if (dev) "ON" else "Off", if (dev) Palette.SKIP else Palette.PASS)
        kv.set("USB debugging", if (adb) "ON" else "Off", if (adb) Palette.SKIP else Palette.PASS)
        if (adb) warnings += "USB debugging on"

        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val secure = km.isDeviceSecure
        kv.set("Screen lock", if (secure) "Set" else "None", if (secure) Palette.PASS else Palette.SKIP)
        if (!secure) warnings += "No screen lock"

        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val enc = when (dpm.storageEncryptionStatus) {
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE, DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER -> "Encrypted"
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY -> "Encrypted (default key)"
            DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED -> "Unsupported"
            else -> "Not encrypted"
        }
        kv.set("Storage encryption", enc, if (enc.startsWith("Encrypted")) Palette.PASS else Palette.SKIP)

        val admins = dpm.activeAdmins.orEmpty().map { it.packageName }.distinct()
        kv.set("Device-admin apps", if (admins.isEmpty()) "None" else admins.joinToString("\n"),
            if (admins.isEmpty()) Palette.PASS else Palette.SKIP)
        if (admins.isNotEmpty()) warnings += "Device admin: ${admins.joinToString(",")}"

        val patch = Build.VERSION.SECURITY_PATCH
        val ageMonths = try {
            val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(patch)
            if (d != null) ((System.currentTimeMillis() - d.time) / (30L * 24 * 3600 * 1000)).toInt() else -1
        } catch (e: Exception) {
            -1
        }
        kv.set("Security patch", if (ageMonths >= 0) "$patch ($ageMonths months old)" else patch,
            if (ageMonths in 0..12) Palette.PASS else Palette.SKIP)
        if (ageMonths > 24) warnings += "Security patch $ageMonths months old"

        val gms = try {
            packageManager.getPackageInfo("com.google.android.gms", 0).versionName ?: "installed"
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        kv.set("Google Play services", gms ?: "Not installed", if (gms != null) Palette.PASS else Palette.SKIP)

        col.addView(label(
            "Tip for used phones: ask the seller to remove their Google / Mi / Samsung account and screen lock before buying, otherwise Factory Reset Protection (FRP) can lock the phone after a reset.",
            12f, Palette.MUTED,
        ))

        when {
            critical.isNotEmpty() -> setStatus("⚠ NOT SECURE: ${critical.joinToString(", ")} — recommend FAIL", Palette.FAIL)
            warnings.isNotEmpty() -> setStatus("Secure, with notes: ${warnings.joinToString(", ")}", Palette.SKIP)
            else -> setStatus("✓ No security issues found — tap Pass", Palette.PASS)
        }
    }

    private fun suFound(): Boolean {
        val paths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/data/local/xbin/su",
            "/data/local/bin/su", "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su",
            "/system/app/Superuser.apk", "/data/adb/magisk", "/sbin/.magisk", "/system/bin/.ext/su",
        )
        return paths.any {
            try {
                File(it).exists()
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun getProp(name: String): String = try {
        val p = Runtime.getRuntime().exec(arrayOf("getprop", name))
        val v = p.inputStream.bufferedReader().readLine()?.trim().orEmpty()
        p.waitFor()
        v
    } catch (e: Exception) {
        ""
    }
}
