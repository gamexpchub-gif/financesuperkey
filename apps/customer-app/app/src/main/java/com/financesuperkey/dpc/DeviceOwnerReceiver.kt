package com.financesuperkey.dpc

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import android.util.Log

/**
 * Device Owner admin component. Referenced by the QR provisioning payload as
 * com.financesuperkey.dpc/.DeviceOwnerReceiver (see the backend's enrollment).
 *
 * Provisioning is the official Android Device Owner QR flow, performed at the
 * point of sale with the customer's consent. No Play Protect bypass, no hidden
 * persistence — only the standard Device Owner capabilities.
 */
class DeviceOwnerReceiver : DeviceAdminReceiver() {

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        val extras: PersistableBundle? =
            intent.getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE)
        applyEnrollmentExtras(context, extras)

        // Launch the app so the customer sees the enrolled status.
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(it)
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        PolicyController(context).onAdminEnabled()
        PolicyWorker.schedule(context)
    }

    private fun applyEnrollmentExtras(context: Context, extras: PersistableBundle?) {
        val store = PolicyStore(context)
        if (extras != null) {
            extras.getString(EXTRA_TOKEN)?.let { store.provisioningToken = it }
            extras.getString(EXTRA_API_BASE)?.let { if (it.isNotBlank()) store.apiBase = it }
            extras.getString(EXTRA_CONTACT)?.let { if (it.isNotBlank()) store.financeContact = it }
        }
        store.enrolled = true
        Log.i(TAG, "Enrollment extras applied; enrolled=true")
        PolicyController(context).onAdminEnabled()
        PolicyWorker.schedule(context)
    }

    companion object {
        private const val TAG = "FSK-DPC"
        const val EXTRA_TOKEN = "com.financesuperkey.PROVISIONING_TOKEN"
        const val EXTRA_API_BASE = "com.financesuperkey.API_BASE"
        const val EXTRA_CONTACT = "com.financesuperkey.FINANCE_CONTACT"

        fun componentName(context: Context): ComponentName =
            ComponentName(context, DeviceOwnerReceiver::class.java)
    }
}
