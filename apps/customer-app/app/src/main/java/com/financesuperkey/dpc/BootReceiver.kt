package com.financesuperkey.dpc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-asserts management on reboot: re-applies Device Owner restrictions, checks
 * the current policy with the server, and shows the restricted screen again if
 * the device is locked. Keeps the lock in place across restarts.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val controller = PolicyController(context)
            controller.onAdminEnabled() // re-apply restrictions
            PolicyWorker.schedule(context)
            PolicyWorker.runNow(context)

            // If already restricted, bring the lock screen straight back.
            val store = PolicyStore(context)
            if (store.isRestricted && controller.isDeviceOwner()) {
                try {
                    context.startActivity(
                        Intent(context, RestrictedActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {
                }
            }
        }
    }
}
