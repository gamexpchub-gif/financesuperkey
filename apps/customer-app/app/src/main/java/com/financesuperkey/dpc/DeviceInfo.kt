package com.financesuperkey.dpc

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager

/**
 * Reads hardware identifiers. On modern Android these are privileged, but a
 * Device Owner app is allowed to read them (the READ_PHONE_STATE grant is set
 * automatically in PolicyController once we are Device Owner). The values are
 * sent to the server, which stores only a masked IMEI.
 */
object DeviceInfo {

    data class Info(val imei: String?, val model: String?, val serial: String?)

    fun read(context: Context): Info {
        val model = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
            .ifBlank { null }

        var imei: String? = null
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            imei = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("MissingPermission") tm.imei
            } else {
                @Suppress("MissingPermission", "DEPRECATION", "HardwareIds") tm.deviceId
            }
        } catch (_: Exception) { /* not readable on this device */ }

        var serial: String? = null
        try {
            serial = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("MissingPermission") Build.getSerial()
            } else {
                @Suppress("DEPRECATION", "HardwareIds") Build.SERIAL
            }
        } catch (_: Exception) { }

        return Info(imei, model, serial)
    }
}
