package com.mxmvncnt.teou.push.unifiedpush

import org.json.JSONObject
import com.mxmvncnt.teou.messaging.WebPushCrypto
import com.mxmvncnt.teou.messaging.PushTransport
import com.mxmvncnt.teou.push.fcm.FcmRelaySender

data class PairPayload(
    val endpoint: String,
    val p256dh: String,
    val auth: String,
    val idPub: String? = null,
    val transport: PushTransport = PushTransport.UNIFIED_PUSH,
    val relayUrl: String? = null,
    val token: String? = null
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
            put("transport", payload.transport.wireName)
            when (payload.transport) {
                PushTransport.UNIFIED_PUSH -> put("e", payload.endpoint)
                PushTransport.FCM -> {
                    put("relayUrl", payload.relayUrl)
                    put("token", payload.token)
                }
            }
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
            val transport = PushTransport.fromWireName(json.optString("transport", "unifiedpush")) ?: return null
            val endpoint = json.optString("e", "")
            val p256dh = json.optString("p", "")
            val auth = json.optString("a", "")
            val relayUrl = json.optString("relayUrl", "").ifBlank { null }
            val token = json.optString("token", "").ifBlank { null }
            if (p256dh.isBlank() || auth.isBlank()) return null
            when (transport) {
                PushTransport.UNIFIED_PUSH -> if (endpoint.isBlank()) return null
                PushTransport.FCM -> if (relayUrl == null || FcmRelaySender.normalizeRelayUrl(relayUrl) == null || token == null) return null
            }
            val idPub = if (json.has("i")) json.optString("i", "").ifBlank { null } else null
            PairPayload(endpoint, p256dh, auth, idPub, transport, relayUrl, token)
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
