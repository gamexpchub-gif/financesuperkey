package com.financesuperkey.dpc

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * The customer fills in their own details and signs here, inside the Finance Key
 * app, after the device is enrolled. Submitted to the server and attached to the
 * dealer's customer record.
 */
class RegistrationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration)

        val name = findViewById<EditText>(R.id.inName)
        val phone = findViewById<EditText>(R.id.inPhone)
        val address = findViewById<EditText>(R.id.inAddress)
        val dealer = findViewById<EditText>(R.id.inDealer)
        val nid = findViewById<EditText>(R.id.inNid)
        val consent = findViewById<CheckBox>(R.id.cbConsent)
        val signature = findViewById<SignatureView>(R.id.signature)

        findViewById<Button>(R.id.btnClearSig).setOnClickListener { signature.clear() }

        findViewById<Button>(R.id.btnSubmit).setOnClickListener {
            if (name.text.isBlank() || phone.text.isBlank()) {
                Toast.makeText(this, "Name and phone are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!consent.isChecked) {
                Toast.makeText(this, "Please confirm consent", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!signature.isSigned()) {
                Toast.makeText(this, "Please sign", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            submit(
                name.text.toString(), phone.text.toString(), address.text.toString(),
                nid.text.toString(), dealer.text.toString()
            )
        }
    }

    private fun submit(
        name: String, phone: String, address: String, nid: String, dealer: String
    ) {
        val btn = findViewById<Button>(R.id.btnSubmit)
        btn.isEnabled = false
        btn.text = getString(R.string.submitting)
        Thread {
            val ok = ApiClient(PolicyStore(this)).registerCustomer(
                name, phone, address, nid, dealer, true
            )
            runOnUiThread {
                if (ok) {
                    Toast.makeText(this, R.string.details_saved, Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    btn.isEnabled = true
                    btn.text = getString(R.string.submit)
                    Toast.makeText(this, R.string.submit_failed, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
