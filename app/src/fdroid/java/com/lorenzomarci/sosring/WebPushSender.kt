package com.lorenzomarci.sosring

import android.util.Log
import com.lorenzomarci.sosring.network.NetworkClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object WebPushSender {

    private const val TAG = "WebPushSender"
    private const val DEFAULT_TTL_SECONDS = 60
    private val OCTET_STREAM = "application/octet-stream".toMediaType()

    fun buildRequest(endpoint: String, body: ByteArray, ttlSeconds: Int = DEFAULT_TTL_SECONDS): Request {
        return Request.Builder()
            .url(endpoint)
            .post(body.toRequestBody(OCTET_STREAM))
            .header("TTL", ttlSeconds.toString())
            .header("Content-Encoding", "aes128gcm")
            .build()
    }

    // Call from a worker thread; keeping the caller alive until the POST finishes matters for push replies.
    fun send(endpoint: String, body: ByteArray, attempts: Int = 1): Boolean {
        for (attempt in 0 until attempts.coerceAtLeast(1)) {
            try {
                Thread.sleep(ControlRetryPolicy.delayBeforeAttempt(attempt))
                NetworkClient.client.newCall(buildRequest(endpoint, body)).execute().use { response ->
                    if (response.isSuccessful) return true
                    Log.e(TAG, "Web Push POST failed: ${response.code} (attempt ${attempt + 1}/$attempts)")
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            } catch (e: Exception) {
                Log.e(TAG, "Web Push POST error: ${e.message} (attempt ${attempt + 1}/$attempts)")
            }
        }
        return false
    }
}
