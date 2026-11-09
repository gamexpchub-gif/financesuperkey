package com.financesuperkey.dpc

import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal HTTP client for the device-facing endpoints. The device authenticates
 * with its one-time provisioning token in the request body (see the backend's
 * device-gateway). Runs on a background thread (called from WorkManager).
 */
class ApiClient(private val store: PolicyStore) {

    data class Policy(
        val policyType: String,
        val emergencyCalls: Boolean,
        val reason: String?,
        val serverDecisionId: String?
    )

    /** Polls the server for the policy this device should currently enforce. */
    fun fetchPolicy(): Policy? {
        val token = store.provisioningToken ?: return null
        val body = JSONObject().put("provisioningToken", token)
        val res = post("/device/policy", body) ?: return null
        return Policy(
            policyType = res.optString("policyType", "normal"),
            emergencyCalls = res.optBoolean("emergencyCalls", true),
            reason = res.optStringOrNull("reason"),
            serverDecisionId = res.optStringOrNull("serverDecisionId")
        )
    }

    fun sendHeartbeat(integrity: JSONObject): Boolean {
        val token = store.provisioningToken ?: return false
        val body = JSONObject()
            .put("provisioningToken", token)
            .put("integrity", integrity)
        return post("/device/heartbeat", body) != null
    }

    fun ackPolicy(serverDecisionId: String?): Boolean {
        val token = store.provisioningToken ?: return false
        val body = JSONObject().put("provisioningToken", token)
        if (serverDecisionId != null) body.put("serverDecisionId", serverDecisionId)
        return post("/device/policy/ack", body) != null
    }

    private fun post(path: String, body: JSONObject): JSONObject? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(store.apiBase + path)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText) ?: return null
            if (code in 200..299) JSONObject(text) else null
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key) || !has(key)) null else optString(key)
