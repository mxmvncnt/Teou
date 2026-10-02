package com.mxmvncnt.teou.messaging

import android.content.Context
import android.util.Log
import com.mxmvncnt.teou.data.PrefsManager
import com.mxmvncnt.teou.util.PhoneUtils
import com.mxmvncnt.teou.util.ControlRetryPolicy
import com.mxmvncnt.teou.location.*
import com.mxmvncnt.teou.push.unifiedpush.WebPushSender
import com.mxmvncnt.teou.push.fcm.FcmRelaySender

object P2pMessaging {

    private const val TAG = "P2pMessaging"
    private const val MAX_ENVELOPE_BYTES = 4096
    private const val LOG_TYPE_INCOMING = "incoming"
    private const val LOG_TYPE_INCOMING_DENIED = "incoming_denied"

    fun requestLocation(context: Context, peer: Peer) {
        sendTo(context, peer, P2pMessageFactory.locRequest())
        Log.i(TAG, "position request sent to ${peer.number}")
    }

    fun handleIncoming(context: Context, envelope: ByteArray) {
        if (envelope.size > MAX_ENVELOPE_BYTES) {
            Log.w(TAG, "Envelope rejected (oversized: ${envelope.size} bytes)")
            return
        }
        val peerStore = PeerStore(context)
        val opened = P2pEnvelope.open(envelope) { peerStore.isTrusted(it) }
        if (opened == null) {
            // Signature-valid but unknown sender: someone holding our endpoint tried to reach us.
            // Recorded for the follower inbox; never acted on.
            P2pEnvelope.openVerified(envelope)?.let {
                FollowerAttempts(context).record(WebPushCrypto.b64enc(it.senderIdPub))
                Log.i(TAG, "Connection attempt recorded from unknown identity")
            } ?: Log.w(TAG, "Envelope rejected (invalid signature or malformed)")
            return
        }
        val sender = peerStore.byIdPub(opened.senderIdPub)
        if (sender == null) {
            Log.w(TAG, "No paired peer for sender identity")
            return
        }
        val type = P2pMessageFactory.type(opened.payload)
        val enforceRateLimit = type == P2pMessageFactory.TYPE_LOC_REQUEST
        val verdict = P2pReplayGuard(context).check(
            senderIdPubB64 = WebPushCrypto.b64enc(opened.senderIdPub),
            ts = P2pMessageFactory.timestamp(opened.payload),
            now = System.currentTimeMillis(),
            enforceRateLimit = enforceRateLimit
        )
        if (verdict != FreshnessVerdict.ACCEPT) {
            Log.w(TAG, "Message rejected from ${sender.number} (freshness: $verdict)")
            return
        }
        when (type) {
            P2pMessageFactory.TYPE_LOC_REQUEST -> respondWithLocation(context, sender)
            P2pMessageFactory.TYPE_LOC_RESPONSE -> storeLocation(context, sender, opened.payload)
            else -> Log.w(TAG, "Unknown P2P message type")
        }
    }

    private fun respondWithLocation(context: Context, requester: Peer) {
        val prefs = PrefsManager(context)
        val contact = prefs.getContacts()
            .firstOrNull { PhoneUtils.matches(requester.number, it.number) }
        val allowed = contact != null && contact.locationEnabled
        val label = contact?.name?.ifBlank { requester.number } ?: requester.number
        prefs.addLocationLog(label, requester.number, if (allowed) LOG_TYPE_INCOMING else LOG_TYPE_INCOMING_DENIED)
        if (!allowed) {
            Log.w(TAG, "Location request from ${requester.number} dropped (sharing not enabled)")
            return
        }
        LocationReplyJob.schedule(context, requester)
    }

    fun sendLocation(context: Context, requester: Peer, latitude: Double, longitude: Double, accuracy: Double) {
        val payload = P2pMessageFactory.locResponse(latitude, longitude, accuracy)
        if (sendTo(context, requester, payload, attempts = ControlRetryPolicy.MAX_ATTEMPTS)) {
            Log.i(TAG, "position response sent to ${requester.number}")
        } else {
            Log.w(TAG, "Position response failed after ${ControlRetryPolicy.MAX_ATTEMPTS} attempts")
        }
    }

    private fun storeLocation(context: Context, sender: Peer, payload: ByteArray) {
        val loc = P2pMessageFactory.parseLocResponse(payload) ?: return
        ReceivedLocationStore(context).save(sender, loc.lat, loc.lon)
        Log.i(TAG, "position response saved from ${sender.number}")
    }

    private fun sendTo(context: Context, peer: Peer, payload: ByteArray, attempts: Int = 1): Boolean {
        val envelope = P2pEnvelope.seal(payload, IdentityKeyStore.idPub(), IdentityKeyStore::sign)
        val body = WebPushCrypto.encrypt(envelope, peer.p256dh, peer.auth)
        return when (peer.transport) {
            PushTransport.UNIFIED_PUSH -> WebPushSender.send(peer.endpoint, body, attempts)
            PushTransport.FCM -> FcmRelaySender.send(
                requireNotNull(peer.relayUrl), requireNotNull(peer.token), body, attempts
            )
        }
    }

}
