package com.gamex.devicetester.tests

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import com.gamex.devicetester.core.BaseTestActivity
import com.gamex.devicetester.ui.KeyValueList
import com.gamex.devicetester.ui.Palette
import com.gamex.devicetester.ui.actionButton
import com.gamex.devicetester.ui.card
import com.gamex.devicetester.ui.dp
import com.gamex.devicetester.ui.dpf
import com.gamex.devicetester.ui.label
import com.gamex.devicetester.ui.lpMatchWrap
import com.gamex.devicetester.ui.roundedBg

/** SIM slots, carrier, network generation and signal. Passes when a SIM is registered on a network. */
class SimNetworkActivity : BaseTestActivity() {
    private lateinit var kv: KeyValueList
    private lateinit var tm: TelephonyManager
    private var note = ""
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 2000)
        }
    }

    override fun currentNote(): String = note

    override fun onCreateTest(savedInstanceState: Bundle?) {
        tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        setInstruction("Insert a SIM card. The app checks SIM slots, operator, network type (2G/3G/4G/5G) and signal. Use 'Test call' to check calling & the earpiece/mic on a real call.")
        val col = scrollColumn()
        val c = card()
        kv = KeyValueList(this)
        c.addView(kv)
        col.addView(c, lpMatchWrap(bottom = dp(12)))
        col.addView(actionButton("📞 Test call (opens dialer)") {
            safeStart(Intent(Intent.ACTION_DIAL, Uri.parse("tel:")))
        }, lpMatchWrap(bottom = dp(8)))
        col.addView(actionButton("Show IMEI (*#06#)", Palette.CARD_ALT) {
            safeStart(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode("*#06#"))))
        }, lpMatchWrap())
        withPermissions(Manifest.permission.READ_PHONE_STATE) {
            handler.removeCallbacks(tick)
            handler.post(tick)
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun refresh() {
        val perm = hasPermission(Manifest.permission.READ_PHONE_STATE)
        kv.set("Phone type", when (tm.phoneType) {
            TelephonyManager.PHONE_TYPE_GSM -> "GSM"
            TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
            TelephonyManager.PHONE_TYPE_SIP -> "SIP"
            else -> "None (no telephony)"
        })
        val slots = if (Build.VERSION.SDK_INT >= 30) tm.activeModemCount else tm.phoneCount
        kv.set("SIM slots", if (slots >= 2) "$slots (Dual SIM)" else slots.toString())
        val state = tm.simState
        val stateText = when (state) {
            TelephonyManager.SIM_STATE_READY -> "Ready"
            TelephonyManager.SIM_STATE_ABSENT -> "No SIM"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN required"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK locked"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Network locked (carrier lock!)"
            TelephonyManager.SIM_STATE_NOT_READY -> "Not ready"
            TelephonyManager.SIM_STATE_PERM_DISABLED -> "Permanently disabled"
            TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "Card error (slot fault?)"
            TelephonyManager.SIM_STATE_CARD_RESTRICTED -> "Card restricted"
            else -> "Unknown"
        }
        kv.set("SIM state", stateText, if (state == TelephonyManager.SIM_STATE_READY) Palette.PASS else Palette.FAIL)
        kv.set("SIM operator", tm.simOperatorName.ifEmpty { "—" })
        val netOp = tm.networkOperatorName
        kv.set("Registered network", netOp.ifEmpty { "Not registered" }, if (netOp.isNotEmpty()) Palette.PASS else Palette.FAIL)
        kv.set("Country", tm.networkCountryIso.uppercase().ifEmpty { "—" })
        kv.set("Roaming", if (tm.isNetworkRoaming) "Yes" else "No")

        var netType = "—"
        if (perm) {
            try {
                netType = networkTypeName(tm.dataNetworkType)
                kv.set("Data network", netType)
                if (Build.VERSION.SDK_INT >= 26) kv.set("Mobile data", if (tm.isDataEnabled) "Enabled" else "Disabled")
            } catch (e: SecurityException) {
                kv.set("Data network", "permission needed")
            }
            try {
                val subs = SubscriptionManager.from(this).activeSubscriptionInfoList
                if (subs.isNullOrEmpty()) {
                    kv.set("Active SIMs", "none")
                } else {
                    subs.forEach { s -> kv.set("SIM ${s.simSlotIndex + 1}", "${s.carrierName} (${s.displayName})", Palette.PASS) }
                }
            } catch (e: Exception) {
                // permission or OEM restriction
            }
        } else {
            kv.set("Data network", "allow phone permission for details", Palette.MUTED)
        }

        var level = -1
        if (Build.VERSION.SDK_INT >= 28) {
            val ss = tm.signalStrength
            if (ss != null) {
                level = ss.level
                val dbm = if (Build.VERSION.SDK_INT >= 29) ss.cellSignalStrengths.firstOrNull()?.dbm else null
                kv.set("Signal", "${"▂▄▆█".take(level.coerceIn(0, 4))} $level/4" + (dbm?.let { " ($it dBm)" } ?: ""))
            }
        }
        note = "${tm.simOperatorName.ifEmpty { netOp }}, $netType" + if (level >= 0) ", signal $level/4" else ""
        if (state == TelephonyManager.SIM_STATE_READY && netOp.isNotEmpty()) {
            autoPass(note, 2500)
        } else if (state == TelephonyManager.SIM_STATE_ABSENT) {
            setStatus("Insert a SIM card to test (or Skip)", Palette.SKIP)
        }
    }

    private fun networkTypeName(t: Int): String = when (t) {
        TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT,
        TelephonyManager.NETWORK_TYPE_IDEN, TelephonyManager.NETWORK_TYPE_GSM -> "2G"
        TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_EHRPD,
        TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G"
        TelephonyManager.NETWORK_TYPE_LTE, TelephonyManager.NETWORK_TYPE_IWLAN -> "4G LTE"
        20 -> "5G NR"
        else -> "Unknown / none"
    }
}

/**
 * IMEI validity (Luhn checksum, known fake IMEIs) and blacklist status via the official
 * government services: BTRC NEIR (Bangladesh) and CEIR (India). Apps cannot read the IMEI on
 * Android 10+, so the tester dials *#06# and types it in.
 */
class ImeiCheckActivity : BaseTestActivity() {
    private lateinit var imei1: EditText
    private lateinit var imei2: EditText
    private lateinit var kv: KeyValueList
    private var validImei: String? = null

    private val knownFake = setOf(
        "000000000000000", "111111111111111", "123456789012345", "012345678901237",
        "004999010640000", "353918050000000", "358673013795895", "352005048247251",
    )

    override fun currentNote(): String? = validImei?.let { "IMEI $it (checksum valid)" }

    override fun onCreateTest(savedInstanceState: Bundle?) {
        setInstruction("1) Dial *#06# to see the IMEI.  2) Type it below and validate.  3) Check blacklist status with the official service, then mark the result.")
        passButton?.text = "✓ Not blocked"
        failButton?.text = "✕ Blocked"

        val col = scrollColumn()
        col.addView(actionButton("📟 Dial *#06# to show IMEI", Palette.CARD_ALT) {
            safeStart(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode("*#06#"))))
        }, lpMatchWrap(bottom = dp(10)))

        imei1 = imeiField("IMEI 1 (15 digits)")
        imei2 = imeiField("IMEI 2 (dual SIM, optional)")
        col.addView(imei1, lpMatchWrap(bottom = dp(8)))
        col.addView(imei2, lpMatchWrap(bottom = dp(8)))
        tryReadImei()

        col.addView(actionButton("✔ Validate IMEI") { validate() }, lpMatchWrap(bottom = dp(10)))

        val c = card()
        kv = KeyValueList(this)
        c.addView(kv)
        col.addView(c, lpMatchWrap(bottom = dp(12)))

        col.addView(label("Blacklist / stolen check (official services)", 15f, Palette.ACCENT, true), lpMatchWrap(bottom = dp(6)))
        col.addView(actionButton("🇧🇩 BTRC NEIR — SMS “KYD <IMEI>” to 16002") { sms("16002", "KYD") }, lpMatchWrap(bottom = dp(8)))
        col.addView(actionButton("🇮🇳 CEIR — SMS “KYM <IMEI>” to 14422") { sms("14422", "KYM") }, lpMatchWrap(bottom = dp(8)))
        col.addView(actionButton("🌐 Online IMEI lookup (imei.info)", Palette.CARD_ALT) {
            safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.imei.info/")))
        }, lpMatchWrap(bottom = dp(8)))
        col.addView(label("The reply SMS tells you if the IMEI is registered, unregistered or blocked. No app can query these blacklists directly without an official/paid API.", 12f, Palette.MUTED))
    }

    private fun imeiField(hint: String) = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_NUMBER
        filters = arrayOf(InputFilter.LengthFilter(15))
        setTextColor(Palette.TEXT)
        setHintTextColor(Palette.MUTED)
        textSize = 18f
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = roundedBg(Palette.CARD, dpf(12f), Palette.STROKE, dp(1))
    }

    @SuppressLint("MissingPermission", "HardwareIds")
    @Suppress("DEPRECATION")
    private fun tryReadImei() {
        if (Build.VERSION.SDK_INT >= 29) return // blocked by Android for normal apps
        if (!hasPermission(Manifest.permission.READ_PHONE_STATE)) return
        try {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (Build.VERSION.SDK_INT >= 26) {
                tm.getImei(0)?.let { imei1.setText(it) }
                tm.getImei(1)?.let { imei2.setText(it) }
            } else {
                tm.deviceId?.let { imei1.setText(it) }
            }
        } catch (e: Exception) {
            // not available
        }
    }

    private fun luhnValid(imei: String): Boolean {
        if (imei.length != 15 || !imei.all { it.isDigit() }) return false
        var sum = 0
        for (i in 0 until 15) {
            var d = imei[14 - i] - '0'
            if (i % 2 == 1) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
        }
        return sum % 10 == 0
    }

    private fun check(label: String, raw: String): Boolean {
        val imei = raw.trim()
        if (imei.isEmpty()) return false
        val ok = luhnValid(imei)
        val fake = imei in knownFake || imei.toSet().size <= 2
        when {
            !ok -> kv.set(label, "✕ INVALID checksum — fake / altered IMEI?", Palette.FAIL)
            fake -> kv.set(label, "⚠ Generic / known fake IMEI", Palette.FAIL)
            else -> kv.set(label, "✓ Valid • TAC ${imei.take(8)}", Palette.PASS)
        }
        return ok && !fake
    }

    private fun validate() {
        val a = check("IMEI 1", imei1.text.toString())
        val bRaw = imei2.text.toString().trim()
        val b = if (bRaw.isNotEmpty()) check("IMEI 2", bRaw) else true
        if (bRaw.isNotEmpty() && bRaw == imei1.text.toString().trim()) {
            kv.set("IMEI 2", "⚠ Same as IMEI 1 — suspicious", Palette.SKIP)
        }
        if (a && b) {
            validImei = imei1.text.toString().trim()
            setStatus("IMEI format OK — now check blacklist status", Palette.PASS)
        } else {
            validImei = null
            setStatus("IMEI problem detected — treat this phone with caution", Palette.FAIL)
        }
    }

    private fun sms(number: String, keyword: String) {
        val imei = validImei
        if (imei == null) {
            setStatus("Validate a correct IMEI first", Palette.SKIP)
            return
        }
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).putExtra("sms_body", "$keyword $imei")
        safeStart(i)
    }
}
