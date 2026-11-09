package com.financesuperkey.dpc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * The restricted ("Payment Required / Under Review") screen. Pinned via lock task
 * so it stays in front while the device is restricted, but:
 *   - Emergency calls are always available (dialer launched with ACTION_DIAL).
 *   - It releases automatically the moment the server policy returns to normal.
 * This is an informational hold, not an irreversible lock.
 */
class RestrictedActivity : AppCompatActivity() {

    private lateinit var store: PolicyStore

    private val releaseReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Server released the restriction — stop pinning and close.
            if (!store.isRestricted) {
                stopLockTaskSafely()
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_restricted)
        store = PolicyStore(this)

        findViewById<TextView>(R.id.reasonText).text =
            store.reason ?: getString(R.string.restricted_default_reason)
        findViewById<TextView>(R.id.contactText).text = store.financeContact

        findViewById<Button>(R.id.btnEmergency).setOnClickListener {
            // Emergency dialer — never blocked.
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:")))
        }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener {
            PolicyWorker.runNow(this)
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(PolicyController.ACTION_RELEASE)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(releaseReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(releaseReceiver, filter)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!store.isRestricted) {
            finish()
            return
        }
        // Pin to the foreground only while this app is the Device Owner.
        if (PolicyController(this).isDeviceOwner()) {
            try {
                startLockTask()
            } catch (_: Exception) { /* lock task may be unavailable */ }
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(releaseReceiver)
        } catch (_: Exception) { }
    }

    private fun stopLockTaskSafely() {
        try {
            stopLockTask()
        } catch (_: Exception) { }
    }

    // Keep the customer on this screen while restricted; back does nothing.
    @Suppress("MissingSuperCall", "GestureBackNavigation")
    override fun onBackPressed() {
        // no-op while restricted
    }
}
