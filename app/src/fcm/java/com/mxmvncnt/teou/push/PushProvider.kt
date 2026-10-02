package com.mxmvncnt.teou.push

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.mxmvncnt.teou.push.fcm.FcmStore
import com.mxmvncnt.teou.messaging.EncryptionKeyStore
import com.mxmvncnt.teou.messaging.IdentityKeyStore
import com.mxmvncnt.teou.messaging.PushTransport
import com.mxmvncnt.teou.messaging.WebPushCrypto
import com.mxmvncnt.teou.push.unifiedpush.PairPayload

object PushProvider {
    fun needsDistributor(context: Context): Boolean = false

    fun pairing(context: Context): PairPayload? {
        val store = FcmStore(context)
        val relay = store.relayUrl?.takeIf { it.isNotBlank() } ?: return null
        val token = store.token?.takeIf { it.isNotBlank() } ?: return null
        val keys = EncryptionKeyStore.getOrCreate(context)
        return PairPayload("", keys.p256dh, keys.authSecret, WebPushCrypto.b64enc(IdentityKeyStore.idPub()),
            PushTransport.FCM, relay, token)
    }

    fun registerStatusListener(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        context.getSharedPreferences("teou_prefs", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterStatusListener(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        context.getSharedPreferences("teou_prefs", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun isRegistered(context: Context): Boolean = FcmStore(context).let {
        !it.token.isNullOrBlank() && !it.relayUrl.isNullOrBlank()
    }

    fun ensureRegistered(activity: Activity) {
        val context = activity.applicationContext
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { FcmStore(context).token = it }
            .addOnFailureListener { Log.w("FcmPushProvider", "Firebase registration failed", it) }
    }
}
