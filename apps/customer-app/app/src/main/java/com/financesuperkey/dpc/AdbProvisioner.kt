package com.financesuperkey.dpc

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Security
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.security.auth.x500.X500Principal

/**
 * In-app ADB self-provisioning. The app uses the device's own Wireless Debugging
 * to connect to localhost adbd and run `dpm set-device-owner` on itself — the
 * same approach Shizuku / App Manager use. This is Android's own developer tool
 * used with the customer's explicit action; it is NOT a security bypass.
 *
 * NOTE: native/ADB code that cannot be unit-tested here; expect to iterate on a
 * real device. Every failure returns a readable message.
 */
object AdbProvisioner {

    private const val HOST = "127.0.0.1"
    private const val DPC = "com.financesuperkey.dpc/.DeviceOwnerReceiver"

    sealed class Result {
        object Success : Result()
        data class Failure(val message: String) : Result()
    }

    /**
     * Pairs (if a pairing code/port is given), connects, and sets Device Owner.
     * Must be called off the main thread.
     */
    fun provision(
        context: Context,
        connectPort: Int,
        pairingPort: Int?,
        pairingCode: String?,
    ): Result {
        return try {
            val manager = Manager.get(context)

            if (pairingPort != null && !pairingCode.isNullOrBlank()) {
                val paired = manager.pair(HOST, pairingPort, pairingCode)
                if (!paired) return Result.Failure("Pairing failed. Check the pairing port and 6-digit code.")
            }

            val connected = manager.connect(HOST, connectPort)
            if (!connected) return Result.Failure("Could not connect to adb on port $connectPort. Check the Wireless debugging port.")

            val output = runShell(manager, "dpm set-device-owner $DPC")
            manager.close()

            if (output.contains("Success", ignoreCase = true)) {
                Result.Success
            } else {
                Result.Failure(output.ifBlank { "set-device-owner returned no output" })
            }
        } catch (e: Exception) {
            Result.Failure(e.message ?: e.toString())
        }
    }

    private fun runShell(manager: AbsAdbConnectionManager, command: String): String {
        val stream: AdbStream = manager.openStream("shell:$command")
        return try {
            stream.openInputStream().bufferedReader().use { it.readText() }
        } finally {
            try { stream.close() } catch (_: Exception) {}
        }
    }

    /** Connection manager that holds a persistent RSA key + self-signed cert. */
    private class Manager private constructor(context: Context) : AbsAdbConnectionManager() {
        private val privateKey: PrivateKey
        private val certificate: Certificate

        init {
            setApi(Build.VERSION.SDK_INT)
            val (key, cert) = loadOrCreate(context)
            privateKey = key
            certificate = cert
        }

        override fun getPrivateKey(): PrivateKey = privateKey
        override fun getCertificate(): Certificate = certificate
        override fun getDeviceName(): String = "FinanceKey"

        companion object {
            @Volatile private var instance: Manager? = null
            fun get(context: Context): Manager =
                instance ?: synchronized(this) {
                    instance ?: Manager(context.applicationContext).also { instance = it }
                }
        }
    }

    private fun loadOrCreate(context: Context): Pair<PrivateKey, Certificate> {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
        // Generate fresh each install; adbd remembers the pairing, not the key file.
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val keyPair: KeyPair = generator.generateKeyPair()
        val cert = selfSignedCert(keyPair.public, keyPair.private)
        // Touch a marker file so repeated runs are quick to reason about.
        File(context.filesDir, "adb_key.marker").writeText("1")
        return keyPair.private to cert
    }

    private fun selfSignedCert(publicKey: PublicKey, privateKey: PrivateKey): X509Certificate {
        val name = X500Name("CN=FinanceKey")
        val now = System.currentTimeMillis()
        val notBefore = java.util.Date(now - TimeUnit.DAYS.toMillis(1))
        val notAfter = java.util.Date(now + TimeUnit.DAYS.toMillis(3650))
        val builder = JcaX509v3CertificateBuilder(
            X500Principal(name.toString()),
            BigInteger.valueOf(now),
            notBefore,
            notAfter,
            X500Principal(name.toString()),
            publicKey
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(privateKey)
        return JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .getCertificate(builder.build(signer))
    }
}
