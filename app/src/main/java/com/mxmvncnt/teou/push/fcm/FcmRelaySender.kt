package com.mxmvncnt.teou.push.fcm

import com.mxmvncnt.teou.messaging.WebPushCrypto
import android.util.Log
import com.mxmvncnt.teou.network.NetworkClient
import com.mxmvncnt.teou.util.ControlRetryPolicy
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

// Shared by both distributions. Sending to FCM requires only HTTP, not Firebase.
object FcmRelaySender {
    fun normalizeRelayUrl(value: String): String? = value.trim().toHttpUrlOrNull()
        ?.takeIf { it.isHttps && it.username.isEmpty() && it.password.isEmpty() &&
            it.query == null && it.fragment == null }
        ?.toString()

    fun buildRequest(relayUrl: String, token: String, body: ByteArray): Request {
        require(token.isNotBlank()) { "Missing FCM recipient token" }
        val base = requireNotNull(normalizeRelayUrl(relayUrl)) { "Invalid FCM relay URL" }
        val payload = WebPushCrypto.b64enc(body)
        require(JSONObject().put("payload", payload).toString().toByteArray().size <= 4096) {
            "FCM payload exceeds the relay limit"
        }
        val json = JSONObject().put("token", token).put("payload", payload)
        val url = base.toHttpUrl().newBuilder()
            .encodedPath(base.toHttpUrl().encodedPath.trimEnd('/') + "/send")
            .build()
        return Request.Builder().url(url)
            .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
    }

    fun send(relayUrl: String, token: String, body: ByteArray, attempts: Int = 1): Boolean {
        val request = buildRequest(relayUrl, token, body)
        for (attempt in 0 until attempts.coerceAtLeast(1)) {
            try {
                Thread.sleep(ControlRetryPolicy.delayBeforeAttempt(attempt))
                NetworkClient.client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) return true
                    Log.w("FcmRelaySender", "FCM relay POST failed: ${response.code} (attempt ${attempt + 1}/$attempts)")
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            } catch (_: Exception) {
                Log.w("FcmRelaySender", "FCM relay POST error (attempt ${attempt + 1}/$attempts)")
            }
        }
        return false
    }
}
