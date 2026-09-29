package com.mxmvncnt.teou.push.unifiedpush

import org.json.JSONObject
import com.mxmvncnt.teou.messaging.WebPushCrypto

data class PairPayload(
    val endpoint: String,
    val p256dh: String,
    val auth: String,
    val idPub: String? = null
)

object UnifiedPushPairing {

    private const val PREFIX = "teou1:"
    const val LINK_SCHEME = "teou"
    private const val LINK_HOST = "pair"
    private const val LINK_PARAM = "data"

    fun linkFor(payload: String): String =
        "$LINK_SCHEME://$LINK_HOST?$LINK_PARAM=${android.net.Uri.encode(payload)}"

    fun encode(payload: PairPayload): String {
        val json = JSONObject().apply {
            put("e", payload.endpoint)
            put("p", payload.p256dh)
            put("a", payload.auth)
            payload.idPub?.let { put("i", it) }
        }
        return PREFIX + WebPushCrypto.b64enc(json.toString().toByteArray(Charsets.UTF_8))
    }

    fun decode(text: String): PairPayload? {
        val trimmed = text.trim()
        val body = if (trimmed.startsWith(PREFIX)) trimmed else linkData(trimmed) ?: return null
        return try {
            val decoded = String(WebPushCrypto.b64dec(body.removePrefix(PREFIX)), Charsets.UTF_8)
            val json = JSONObject(decoded)
            val endpoint = json.optString("e", "")
            val p256dh = json.optString("p", "")
            val auth = json.optString("a", "")
            if (endpoint.isBlank() || p256dh.isBlank() || auth.isBlank()) return null
            val idPub = if (json.has("i")) json.optString("i", "").ifBlank { null } else null
            PairPayload(endpoint, p256dh, auth, idPub)
        } catch (e: Exception) {
            null
        }
    }

    private fun linkData(text: String): String? = try {
        val uri = android.net.Uri.parse(text)
        if (uri.scheme == LINK_SCHEME && uri.host == LINK_HOST) uri.getQueryParameter(LINK_PARAM) else null
    } catch (_: Exception) {
        null
    }
}
