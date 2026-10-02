package com.mxmvncnt.teou.messaging

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import com.mxmvncnt.teou.util.PhoneUtils
import com.mxmvncnt.teou.location.ReceivedLocationStore

data class Peer(
    val number: String,
    val endpoint: String,
    val p256dh: String,
    val auth: String,
    val idPub: String,
    val transport: PushTransport = PushTransport.UNIFIED_PUSH,
    val relayUrl: String? = null,
    val token: String? = null
)

class PeerStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("teou_peers", Context.MODE_PRIVATE)

    fun all(): List<Peer> {
        val json = prefs.getString(KEY_PEERS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val transport = requireNotNull(PushTransport.fromWireName(obj.optString("transport", "unifiedpush")))
                Peer(
                    number = obj.getString("number"),
                    endpoint = obj.optString("endpoint", ""),
                    p256dh = obj.getString("p256dh"),
                    auth = obj.getString("auth"),
                    idPub = obj.getString("idPub"),
                    transport = transport,
                    relayUrl = obj.optString("relayUrl", "").ifBlank { null },
                    token = obj.optString("token", "").ifBlank { null }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun get(number: String): Peer? {
        val target = PhoneUtils.normalize(number)
        return all().firstOrNull { PhoneUtils.normalize(it.number) == target }
    }

    fun save(peer: Peer) {
        val normalized = peer.copy(number = PhoneUtils.normalize(peer.number))
        val target = normalized.number
        get(target)?.takeIf { it.idPub != normalized.idPub }?.let { ReceivedLocationStore(context).remove(it) }
        FollowerAttempts(context).remove(normalized.idPub)
        persist(all().filter { PhoneUtils.normalize(it.number) != target } + normalized)
    }

    fun remove(number: String) {
        val target = PhoneUtils.normalize(number)
        get(target)?.let { ReceivedLocationStore(context).remove(it) }
        persist(all().filter { PhoneUtils.normalize(it.number) != target })
    }

    fun isTrusted(idPub: ByteArray): Boolean {
        val target = WebPushCrypto.b64enc(idPub)
        return all().any { it.idPub == target }
    }

    fun byIdPub(idPub: ByteArray): Peer? {
        val target = WebPushCrypto.b64enc(idPub)
        return matchSingleByIdPub(all(), target)
    }

    private fun persist(peers: List<Peer>) {
        val arr = JSONArray()
        peers.forEach { peer ->
            arr.put(JSONObject().apply {
                put("number", peer.number)
                put("endpoint", peer.endpoint)
                put("p256dh", peer.p256dh)
                put("auth", peer.auth)
                put("idPub", peer.idPub)
                put("transport", peer.transport.wireName)
                put("relayUrl", peer.relayUrl)
                put("token", peer.token)
            })
        }
        prefs.edit().putString(KEY_PEERS, arr.toString()).apply()
    }

    companion object {
        private const val KEY_PEERS = "peers"

        internal fun matchSingleByIdPub(peers: List<Peer>, idPubB64: String): Peer? {
            val matches = peers.filter { it.idPub == idPubB64 }
            return matches.singleOrNull()
        }
    }
}
