package com.financesuperkey.dpc

import android.content.Context

/**
 * Local persisted state for the device policy controller: the enrollment token,
 * the server address, and the last policy we applied. Stored in SharedPreferences.
 */
class PolicyStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("fsk_dpc", Context.MODE_PRIVATE)

    var apiBase: String
        get() = prefs.getString(KEY_API_BASE, DEFAULT_API_BASE) ?: DEFAULT_API_BASE
        set(value) = prefs.edit().putString(KEY_API_BASE, value).apply()

    var provisioningToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    /** normal | reminder | restricted */
    var policyType: String
        get() = prefs.getString(KEY_POLICY, "normal") ?: "normal"
        set(value) = prefs.edit().putString(KEY_POLICY, value).apply()

    var reason: String?
        get() = prefs.getString(KEY_REASON, null)
        set(value) = prefs.edit().putString(KEY_REASON, value).apply()

    var financeContact: String
        get() = prefs.getString(KEY_CONTACT, DEFAULT_CONTACT) ?: DEFAULT_CONTACT
        set(value) = prefs.edit().putString(KEY_CONTACT, value).apply()

    var enrolled: Boolean
        get() = prefs.getBoolean(KEY_ENROLLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENROLLED, value).apply()

    val isRestricted: Boolean get() = policyType == "restricted"

    companion object {
        private const val KEY_API_BASE = "api_base"
        private const val KEY_TOKEN = "token"
        private const val KEY_POLICY = "policy"
        private const val KEY_REASON = "reason"
        private const val KEY_CONTACT = "contact"
        private const val KEY_ENROLLED = "enrolled"

        // Overridable by the QR provisioning bundle (com.financesuperkey.API_BASE).
        const val DEFAULT_API_BASE = "https://admin.deencircle.online/financekey/api/v1"
        const val DEFAULT_CONTACT = "Contact your finance provider"
    }
}
