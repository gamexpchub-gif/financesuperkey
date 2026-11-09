package com.financesuperkey.dpc

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Applies the server's policy decision using the standard Device Owner APIs.
 *
 * Legitimate-use guardrails baked in:
 *  - Only acts when this app is actually the Device Owner (consented enrollment).
 *  - "restricted" shows a pinned informational screen (payment notice + contact).
 *  - Emergency calls are never blocked.
 *  - "normal" fully releases the device. The client never decides on its own —
 *    the policy always comes from the server.
 */
class PolicyController(private val context: Context) {

    private val dpm =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = DeviceOwnerReceiver.componentName(context)
    private val store = PolicyStore(context)

    fun isDeviceOwner(): Boolean =
        dpm.isDeviceOwnerApp(context.packageName)

    /** One-time setup when the admin becomes active. */
    fun onAdminEnabled() {
        if (!isDeviceOwner()) return
        try {
            // Allow only this app to enter lock-task (kiosk) mode.
            dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Keep global actions + system info available; emergency stays on.
                dpm.setLockTaskFeatures(
                    admin,
                    DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS or
                        DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO or
                        DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "onAdminEnabled: ${e.message}")
        }
    }

    /**
     * Applies a freshly fetched policy. Persists it, then shows or dismisses the
     * restricted screen. Returns true if the state changed.
     */
    fun apply(policy: ApiClient.Policy): Boolean {
        val changed = policy.policyType != store.policyType
        store.policyType = policy.policyType
        store.reason = policy.reason

        if (!isDeviceOwner()) return changed // nothing to enforce without ownership

        if (policy.policyType == "restricted") {
            launchRestricted()
        } else {
            // Ask the restricted screen (if visible) to finish and release.
            context.sendBroadcast(Intent(ACTION_RELEASE).setPackage(context.packageName))
        }
        return changed
    }

    private fun launchRestricted() {
        try {
            val intent = Intent(context, RestrictedActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "launchRestricted: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "FSK-DPC"
        const val ACTION_RELEASE = "com.financesuperkey.dpc.RELEASE"
    }
}
