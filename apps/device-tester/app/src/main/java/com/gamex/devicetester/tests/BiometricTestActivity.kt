package com.gamex.devicetester.tests

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.gamex.devicetester.core.BaseTestActivity
import com.gamex.devicetester.ui.KeyValueList
import com.gamex.devicetester.ui.Palette
import com.gamex.devicetester.ui.actionButton
import com.gamex.devicetester.ui.card
import com.gamex.devicetester.ui.dp
import com.gamex.devicetester.ui.lpMatchWrap

/**
 * Fingerprint / face test through the official BiometricPrompt. Android does not let apps pick
 * the modality, so the face test uses the "weak" class (where face unlock usually lives) and the
 * fingerprint test the "strong" class.
 */
class BiometricTestActivity : BaseTestActivity() {
    companion object {
        const val EXTRA_MODE = "bio_mode"
        const val MODE_FINGER = "finger"
        const val MODE_FACE = "face"
    }

    private val finger get() = intent.getStringExtra(EXTRA_MODE) != MODE_FACE
    private val authenticator get() = if (finger) BIOMETRIC_STRONG else BIOMETRIC_WEAK
    private var badReads = 0
    private lateinit var kv: KeyValueList

    override fun currentNote(): String = if (badReads > 0) "Sensor read $badReads non-matching attempts" else ""

    override fun onCreateTest(savedInstanceState: Bundle?) {
        val pm = packageManager
        val hasFinger = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        val hasFace = Build.VERSION.SDK_INT >= 29 && pm.hasSystemFeature(PackageManager.FEATURE_FACE)
        val hasIris = Build.VERSION.SDK_INT >= 29 && pm.hasSystemFeature(PackageManager.FEATURE_IRIS)
        val bm = BiometricManager.from(this)

        setInstruction(
            if (finger) "Tap Start and touch the sensor with an ENROLLED finger. A wrong finger proves the sensor reads, but only an enrolled finger passes."
            else "Tap Start and look at the front camera. Note: many phones (some Xiaomi, Samsung, Realme models) don't expose face unlock to apps — then test it from the lock screen and mark Pass / Fail manually.",
        )

        val col = scrollColumn()
        val c = card()
        kv = KeyValueList(this)
        kv.set("Fingerprint hardware", yesNo(hasFinger), if (hasFinger) Palette.PASS else Palette.MUTED)
        kv.set("Face hardware (for apps)", yesNo(hasFace), if (hasFace) Palette.PASS else Palette.MUTED)
        kv.set("Iris hardware", yesNo(hasIris))
        kv.set("Strong biometric", describe(bm.canAuthenticate(BIOMETRIC_STRONG)))
        kv.set("Weak biometric (face)", describe(bm.canAuthenticate(BIOMETRIC_WEAK)))
        c.addView(kv)
        col.addView(c, lpMatchWrap(bottom = dp(12)))

        col.addView(actionButton(if (finger) "☝️ Start fingerprint scan" else "🙂 Start face scan") { start() }, lpMatchWrap(bottom = dp(8)))
        col.addView(actionButton("⚙ Enroll / open security settings", Palette.CARD_ALT) { openEnroll() }, lpMatchWrap())

        if (finger && !hasFinger) setStatus("No fingerprint sensor reported by this phone", Palette.FAIL)
    }

    private fun yesNo(b: Boolean) = if (b) "Yes" else "No"

    private fun describe(code: Int): String = when (code) {
        BiometricManager.BIOMETRIC_SUCCESS -> "Ready (enrolled)"
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "Present, not enrolled"
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "No hardware"
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Hardware unavailable"
        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "Security update required"
        else -> "Unsupported ($code)"
    }

    private fun start() {
        val bm = BiometricManager.from(this)
        when (val r = bm.canAuthenticate(authenticator)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Unit
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                setStatus("Nothing enrolled — tap 'Enroll' to add a ${if (finger) "fingerprint" else "face"} first", Palette.SKIP)
                return
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                setStatus("Biometric hardware unavailable (busy or faulty)", Palette.FAIL)
                return
            }
            else -> {
                setStatus("Not available to apps: ${describe(r)}", Palette.FAIL)
                return
            }
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                autoPass(if (finger) "Fingerprint matched" else "Biometric (face/weak) matched")
            }

            override fun onAuthenticationFailed() {
                badReads++
                setStatus("Not recognised — sensor IS reading. Try an enrolled ${if (finger) "finger" else "face"}.", Palette.SKIP)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val hw = errorCode == BiometricPrompt.ERROR_HW_UNAVAILABLE || errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT
                setStatus("Error: $errString", if (hw) Palette.FAIL else Palette.MUTED)
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(if (finger) "Fingerprint test" else "Face unlock test")
            .setSubtitle(if (finger) "Touch the fingerprint sensor" else "Look at the screen")
            .setAllowedAuthenticators(authenticator)
            .setNegativeButtonText("Cancel")
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info)
    }

    private fun openEnroll() {
        val fallback = Intent(Settings.ACTION_SECURITY_SETTINGS)
        val i = when {
            Build.VERSION.SDK_INT >= 30 -> Intent(Settings.ACTION_BIOMETRIC_ENROLL)
                .putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, authenticator)
            Build.VERSION.SDK_INT >= 28 && finger -> Intent(Settings.ACTION_FINGERPRINT_ENROLL)
            else -> fallback
        }
        safeStart(i, fallback)
    }
}
