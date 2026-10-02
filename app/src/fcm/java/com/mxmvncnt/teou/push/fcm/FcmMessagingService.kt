package com.mxmvncnt.teou.push.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.util.Log
import com.mxmvncnt.teou.messaging.EncryptionKeyStore
import com.mxmvncnt.teou.messaging.P2pMessaging
import com.mxmvncnt.teou.messaging.WebPushCrypto

class FcmMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        FcmStore(this).token = token
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val payload = message.data["payload"] ?: return
        val envelope = try {
            require(payload.length <= 4096) { "Oversized FCM payload" }
            val keys = EncryptionKeyStore.getOrCreate(this)
            WebPushCrypto.decrypt(WebPushCrypto.b64dec(payload), keys.privateKey, keys.publicKey, keys.auth)
        } catch (_: Exception) {
            Log.w("FcmMessagingService", "FCM payload rejected (invalid encryption or local keys)")
            return
        }
        P2pMessaging.handleIncoming(this, envelope)
    }
}
