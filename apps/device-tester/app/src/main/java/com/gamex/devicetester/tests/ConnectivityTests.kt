package com.gamex.devicetester.tests

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import com.gamex.devicetester.core.BaseTestActivity
import com.gamex.devicetester.ui.KeyValueList
import com.gamex.devicetester.ui.Palette
import com.gamex.devicetester.ui.actionButton
import com.gamex.devicetester.ui.card
import com.gamex.devicetester.ui.dp
import com.gamex.devicetester.ui.lpMatchWrap
import java.net.HttpURLConnection
import java.net.URL

private fun Context.kvCard(parent: android.widget.LinearLayout): KeyValueList {
    val c = card()
    val kv = KeyValueList(this)
    c.addView(kv)
    parent.addView(c, lpMatchWrap(bottom = dp(12)))
    return kv
}

/** Wi-Fi radio, connection quality and a real internet reachability check. */
class WifiTestActivity : BaseTestActivity() {
    private lateinit var kv: KeyValueList
    private var internetOk = false
    private var checking = false
    private var note = ""
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1500)
        }
    }

    override fun currentNote(): String = note

    override fun onCreateTest(savedInstanceState: Bundle?) {
        setInstruction("Turn Wi-Fi on and connect to a network. The app checks signal, speed, band and real internet access over Wi-Fi.")
        val col = scrollColumn()
        kv = kvCard(col)
        col.addView(actionButton("Open Wi-Fi settings", Palette.CARD_ALT) {
            safeStart(
                if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS),
                Intent(Settings.ACTION_WIFI_SETTINGS),
            )
        }, lpMatchWrap())
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    @Suppress("DEPRECATION")
    private fun refresh() {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val enabled = wm.isWifiEnabled
        kv.set("Wi-Fi radio", if (enabled) "ON" else "OFF", if (enabled) Palette.PASS else Palette.FAIL)
        kv.set("5 GHz support", if (wm.is5GHzBandSupported) "Yes" else "No")
        if (Build.VERSION.SDK_INT >= 30) kv.set("6 GHz support", if (wm.is6GHzBandSupported) "Yes" else "No")

        val wifiNet = cm.allNetworks.firstOrNull { n ->
            cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        val connected = wifiNet != null
        kv.set("Connected", if (connected) "Yes" else "No", if (connected) Palette.PASS else Palette.MUTED)
        if (connected) {
            val info = wm.connectionInfo
            val rssi = info.rssi
            val quality = when {
                rssi >= -55 -> "Excellent"
                rssi >= -67 -> "Good"
                rssi >= -75 -> "Fair"
                else -> "Weak"
            }
            kv.set("Signal", "$rssi dBm ($quality)")
            kv.set("Link speed", "${info.linkSpeed} Mbps")
            val f = info.frequency
            val band = when {
                f >= 5925 -> "6 GHz"
                f >= 4900 -> "5 GHz"
                f > 0 -> "2.4 GHz"
                else -> "?"
            }
            kv.set("Frequency", "$f MHz ($band)")
            if (Build.VERSION.SDK_INT >= 30) {
                val std = when (info.wifiStandard) {
                    1 -> "Legacy (a/b/g)"
                    4 -> "Wi-Fi 4 (n)"
                    5 -> "Wi-Fi 5 (ac)"
                    6 -> "Wi-Fi 6 (ax)"
                    7 -> "WiGig (ad)"
                    8 -> "Wi-Fi 7 (be)"
                    else -> "Unknown"
                }
                kv.set("Standard", std)
            }
            note = "RSSI $rssi dBm, ${info.linkSpeed} Mbps, $band"
            if (!internetOk && !checking) checkInternet(wifiNet!!)
        } else {
            setStatus(if (enabled) "Wi-Fi is on but not connected to a network" else "Turn on Wi-Fi", Palette.MUTED)
        }
    }

    private fun checkInternet(net: android.net.Network) {
        checking = true
        kv.set("Internet (via Wi-Fi)", "checking…", Palette.MUTED)
        Thread {
            val t0 = SystemClock.elapsedRealtime()
            val ok = try {
                val conn = net.openConnection(URL("https://connectivitycheck.gstatic.com/generate_204")) as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val code = conn.responseCode
                conn.disconnect()
                code == 204 || code == 200
            } catch (e: Exception) {
                false
            }
            val ms = SystemClock.elapsedRealtime() - t0
            runOnUiThread {
                checking = false
                if (ok) {
                    internetOk = true
                    kv.set("Internet (via Wi-Fi)", "OK • $ms ms", Palette.PASS)
                    note += ", internet OK ($ms ms)"
                    autoPass(note, 2000)
                } else {
                    kv.set("Internet (via Wi-Fi)", "No internet", Palette.FAIL)
                    setStatus("Connected but no internet — radio works; check the router", Palette.SKIP)
                }
            }
        }.start()
    }
}

/** Bluetooth adapter state + a BLE scan that must find at least one nearby device. */
class BluetoothTestActivity : BaseTestActivity() {
    private lateinit var kv: KeyValueList
    private var adapter: BluetoothAdapter? = null
    private val found = HashSet<String>()
    private var scanning = false
    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (found.add(result.device.address)) {
                kv.set("Nearby devices found", found.size.toString(), Palette.PASS)
                if (found.size >= 1) {
                    stopScan()
                    autoPass("Adapter ON, BLE scan found ${found.size} device(s)", 1500)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            setStatus("Scan failed (code $errorCode)", Palette.FAIL)
            scanning = false
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1500)
        }
    }

    override fun currentNote(): String = "Found ${found.size} nearby device(s)"

    override fun onCreateTest(savedInstanceState: Bundle?) {
        val bm = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
        adapter = bm?.adapter
        setInstruction("Turn Bluetooth on, then tap Scan. Passes when at least one nearby Bluetooth device (phone, watch, earbuds, TV…) is detected.")
        val col = scrollColumn()
        kv = kvCard(col)
        if (adapter == null) {
            kv.set("Bluetooth hardware", "Not found", Palette.FAIL)
            setStatus("No Bluetooth adapter on this device", Palette.FAIL)
            return
        }
        kv.set("Bluetooth hardware", "Present", Palette.PASS)
        kv.set("Bluetooth LE", if (packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) "Supported" else "No")
        col.addView(actionButton("🔍 Scan for nearby devices") { requestScan() }, lpMatchWrap(bottom = dp(8)))
        col.addView(actionButton("Open Bluetooth settings", Palette.CARD_ALT) {
            safeStart(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }, lpMatchWrap())
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
        stopScan()
    }

    private fun refresh() {
        val a = adapter ?: return
        val on = try {
            a.isEnabled
        } catch (e: SecurityException) {
            false
        }
        kv.set("Bluetooth radio", if (on) "ON" else "OFF", if (on) Palette.PASS else Palette.FAIL)
    }

    private fun requestScan() {
        val perms = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        withPermissions(*perms) { ok ->
            if (ok) startScan() else setStatus("Bluetooth / location permission denied", Palette.FAIL)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        val a = adapter ?: return
        if (!a.isEnabled) {
            setStatus("Turn Bluetooth ON first", Palette.SKIP)
            return
        }
        val scanner = a.bluetoothLeScanner
        if (scanner == null) {
            setStatus("BLE scanner unavailable", Palette.FAIL)
            return
        }
        try {
            scanner.startScan(scanCb)
            scanning = true
            setStatus("Scanning for 15 seconds…", Palette.MUTED)
            handler.postDelayed({
                if (scanning) {
                    stopScan()
                    if (found.isEmpty()) setStatus("No devices found — radio is ON; try near another Bluetooth device", Palette.SKIP)
                }
            }, 15_000)
        } catch (e: Exception) {
            setStatus("Scan error: ${e.message}", Palette.FAIL)
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (!scanning) return
        scanning = false
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCb)
        } catch (e: Exception) {
            // ignore
        }
    }
}

/** GNSS satellites per constellation, signal strength and time-to-first-fix. */
class GpsTestActivity : BaseTestActivity() {
    private lateinit var kv: KeyValueList
    private lateinit var lm: LocationManager
    private var started = false
    private var startTime = 0L
    private var visible = 0
    private var used = 0
    private var note = ""

    private val gnssCb = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            visible = status.satelliteCount
            used = 0
            val byConst = sortedMapOf<String, Int>()
            val cn0 = mutableListOf<Float>()
            for (i in 0 until status.satelliteCount) {
                if (status.usedInFix(i)) used++
                val name = when (status.getConstellationType(i)) {
                    GnssStatus.CONSTELLATION_GPS -> "GPS"
                    GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
                    GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
                    GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou"
                    GnssStatus.CONSTELLATION_QZSS -> "QZSS"
                    GnssStatus.CONSTELLATION_SBAS -> "SBAS"
                    7 -> "NavIC"
                    else -> "Other"
                }
                byConst[name] = (byConst[name] ?: 0) + 1
                cn0 += status.getCn0DbHz(i)
            }
            kv.set("Satellites visible", visible.toString(), if (visible > 0) Palette.PASS else Palette.MUTED)
            kv.set("Used in fix", used.toString())
            kv.set("Constellations", byConst.entries.joinToString(", ") { "${it.key} ${it.value}" }.ifEmpty { "—" })
            val top = cn0.sortedDescending().take(4)
            if (top.isNotEmpty()) kv.set("Best signal (avg top 4)", String.format("%.0f dB-Hz", top.average()))
            if (!isTestDone()) setStatus("Searching… $visible satellites visible, $used used", Palette.MUTED)
        }
    }

    private val locListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val ttff = (SystemClock.elapsedRealtime() - startTime) / 1000
            kv.set("Position", String.format("%.5f, %.5f", location.latitude, location.longitude), Palette.PASS)
            kv.set("Accuracy", String.format("± %.0f m", location.accuracy))
            kv.set("Time to fix", "$ttff s")
            note = String.format("Fix in %d s, %d/%d satellites, ±%.0f m", ttff, used, visible, location.accuracy)
            autoPass(note, 2000)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun currentNote(): String = note.ifEmpty { "$visible satellites visible, $used used (no fix)" }

    override fun onCreateTest(savedInstanceState: Bundle?) {
        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        setInstruction("Go near a window or outdoors. The app counts satellites (GPS, GLONASS, Galileo, BeiDou…) and waits for a position fix. Indoors a fix can take 1–2 minutes.")
        val col = scrollColumn()
        kv = kvCard(col)
        col.addView(actionButton("Open location settings", Palette.CARD_ALT) {
            safeStart(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }, lpMatchWrap())
        withPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION) { ok ->
            if (ok) start() else setStatus("Location permission denied", Palette.FAIL)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!started && hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) start()
    }

    @SuppressLint("MissingPermission")
    private fun start() {
        if (started) return
        val on = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        kv.set("Location / GPS", if (on) "ON" else "OFF", if (on) Palette.PASS else Palette.FAIL)
        if (!on) {
            setStatus("Turn on Location, then come back", Palette.SKIP)
            return
        }
        try {
            lm.registerGnssStatusCallback(gnssCb, handler)
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locListener, Looper.getMainLooper())
            startTime = SystemClock.elapsedRealtime()
            started = true
            setStatus("Searching for satellites…", Palette.MUTED)
        } catch (e: Exception) {
            setStatus("GPS error: ${e.message}", Palette.FAIL)
        }
    }

    override fun onDestroy() {
        try {
            lm.removeUpdates(locListener)
            lm.unregisterGnssStatusCallback(gnssCb)
        } catch (e: Exception) {
            // ignore
        }
        super.onDestroy()
    }
}

/** NFC on/off and a real tag / card read. */
class NfcTestActivity : BaseTestActivity() {
    private lateinit var kv: KeyValueList
    private var adapter: NfcAdapter? = null
    private var note = ""

    override fun currentNote(): String = note

    override fun onCreateTest(savedInstanceState: Bundle?) {
        adapter = NfcAdapter.getDefaultAdapter(this)
        setInstruction("Turn NFC on, then hold an NFC card or tag (bank/metro card, ID card, NFC sticker) against the back of the phone.")
        val col = scrollColumn()
        kv = kvCard(col)
        col.addView(actionButton("Open NFC settings", Palette.CARD_ALT) {
            safeStart(Intent(Settings.ACTION_NFC_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }, lpMatchWrap())
        if (adapter == null) {
            kv.set("NFC hardware", "Not found", Palette.FAIL)
            setStatus("This phone has no NFC", Palette.FAIL)
        } else {
            kv.set("NFC hardware", "Present", Palette.PASS)
        }
    }

    override fun onResume() {
        super.onResume()
        val a = adapter ?: return
        val on = a.isEnabled
        kv.set("NFC", if (on) "ON" else "OFF", if (on) Palette.PASS else Palette.FAIL)
        if (!on) {
            setStatus("Turn NFC on to test tag reading", Palette.SKIP)
            return
        }
        setStatus("Waiting for a card / tag…", Palette.MUTED)
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V
        a.enableReaderMode(this, { tag: Tag ->
            val id = tag.id.joinToString(":") { String.format("%02X", it) }
            val techs = tag.techList.joinToString(", ") { it.substringAfterLast('.') }
            runOnUiThread {
                kv.set("Tag ID", id, Palette.PASS)
                kv.set("Technologies", techs)
                note = "Tag read: $techs"
                autoPass(note, 1500)
            }
        }, flags, null)
    }

    override fun onPause() {
        super.onPause()
        try {
            adapter?.disableReaderMode(this)
        } catch (e: Exception) {
            // ignore
        }
    }
}
