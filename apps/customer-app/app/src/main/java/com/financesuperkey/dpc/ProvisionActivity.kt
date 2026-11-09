package com.financesuperkey.dpc

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Guides the customer/dealer through in-app Device Owner setup using the phone's
 * own Wireless Debugging. The customer enables wireless debugging, reads the
 * ports/code shown by the system, enters them here, and the app pairs with its
 * own adbd over localhost and sets itself as Device Owner.
 */
class ProvisionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_provision)

        val connectPort = findViewById<EditText>(R.id.inConnectPort)
        val pairPort = findViewById<EditText>(R.id.inPairPort)
        val pairCode = findViewById<EditText>(R.id.inPairCode)
        val status = findViewById<TextView>(R.id.status)
        val btn = findViewById<Button>(R.id.btnProvision)

        btn.setOnClickListener {
            val cPort = connectPort.text.toString().trim().toIntOrNull()
            if (cPort == null) {
                Toast.makeText(this, "Enter the Wireless debugging port", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val pPort = pairPort.text.toString().trim().toIntOrNull()
            val code = pairCode.text.toString().trim().ifBlank { null }

            btn.isEnabled = false
            status.text = getString(R.string.provisioning)
            Thread {
                val result = AdbProvisioner.provision(this, cPort, pPort, code)
                runOnUiThread {
                    btn.isEnabled = true
                    when (result) {
                        is AdbProvisioner.Result.Success -> {
                            PolicyController(this).onAdminEnabled()
                            status.text = getString(R.string.provision_ok)
                            Toast.makeText(this, R.string.provision_ok, Toast.LENGTH_LONG).show()
                            finish()
                        }
                        is AdbProvisioner.Result.Failure -> {
                            status.text = getString(R.string.provision_failed, result.message)
                        }
                    }
                }
            }.start()
        }
    }
}
