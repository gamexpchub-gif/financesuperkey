package com.financesuperkey.dpc

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Customer-facing status screen. Shows enrollment state and the current device
 * status. If the server has restricted the device, routes to RestrictedActivity.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var store: PolicyStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = PolicyStore(this)

        findViewById<Button>(R.id.btnCheck).setOnClickListener {
            if (store.provisioningToken == null) {
                showEnrollDialog()
            } else {
                PolicyWorker.runNow(this)
                Toast.makeText(this, R.string.checking, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.btnSupport).setOnClickListener {
            Toast.makeText(this, store.financeContact, Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (store.isRestricted) {
            startActivity(Intent(this, RestrictedActivity::class.java))
            return
        }
        render()
        if (store.provisioningToken != null) PolicyWorker.runNow(this)
    }

    private fun render() {
        val controller = PolicyController(this)
        val enrolled = store.enrolled && store.provisioningToken != null

        findViewById<TextView>(R.id.statusBadge).apply {
            text = if (enrolled) getString(R.string.status_active)
            else getString(R.string.status_not_enrolled)
            setBackgroundResource(
                if (enrolled) R.drawable.badge_good else R.drawable.badge_muted
            )
            setTextColor(
                getColor(if (enrolled) R.color.good else R.color.muted)
            )
        }
        findViewById<TextView>(R.id.statusDetail).text =
            if (enrolled) getString(R.string.active_detail)
            else getString(R.string.not_enrolled_detail)

        findViewById<TextView>(R.id.contactValue).text = store.financeContact

        // When device-owner but not yet linked to a server token, offer manual
        // enrollment (used for ADB provisioning and as a fallback to QR).
        findViewById<Button>(R.id.btnCheck).text =
            if (store.provisioningToken == null && controller.isDeviceOwner())
                getString(R.string.enroll_device)
            else getString(R.string.check_now)
    }

    private fun showEnrollDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val serverInput = EditText(this).apply {
            hint = "Server URL"
            setText(store.apiBase)
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        val tokenInput = EditText(this).apply {
            hint = "Enrollment token"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        container.addView(serverInput)
        container.addView(tokenInput)

        AlertDialog.Builder(this)
            .setTitle(R.string.enroll_device)
            .setView(container)
            .setPositiveButton(R.string.enroll_device) { _, _ ->
                val token = tokenInput.text.toString().trim()
                val server = serverInput.text.toString().trim()
                if (token.isEmpty()) {
                    Toast.makeText(this, "Token is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (server.isNotEmpty()) store.apiBase = server
                store.provisioningToken = token
                store.enrolled = true
                PolicyWorker.schedule(this)
                PolicyWorker.runNow(this)
                Toast.makeText(this, R.string.checking, Toast.LENGTH_SHORT).show()
                render()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
