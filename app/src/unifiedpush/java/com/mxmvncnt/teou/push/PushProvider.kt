package com.mxmvncnt.teou.push

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.mxmvncnt.teou.push.unifiedpush.UnifiedPushStore
import com.mxmvncnt.teou.push.unifiedpush.PairPayload
import com.mxmvncnt.teou.messaging.IdentityKeyStore
import com.mxmvncnt.teou.messaging.WebPushCrypto
import org.unifiedpush.android.connector.UnifiedPush

object PushProvider {

    fun needsDistributor(context: Context): Boolean = UnifiedPush.getDistributors(context).isEmpty()

    fun pairing(context: Context): PairPayload? {
        val store = UnifiedPushStore(context)
        val endpoint = store.endpointUrl?.takeIf { it.isNotBlank() } ?: return null
        val publicKey = store.pubKey?.takeIf { it.isNotBlank() } ?: return null
        val auth = store.auth?.takeIf { it.isNotBlank() } ?: return null
        return PairPayload(endpoint, publicKey, auth, WebPushCrypto.b64enc(IdentityKeyStore.idPub()))
    }

    fun registerStatusListener(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        context.getSharedPreferences("teou_unifiedpush", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterStatusListener(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        context.getSharedPreferences("teou_unifiedpush", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun isRegistered(context: Context): Boolean = !UnifiedPushStore(context).endpointUrl.isNullOrBlank()

    fun ensureRegistered(activity: Activity) {
        val ack = UnifiedPush.getAckDistributor(activity)
        val saved = UnifiedPush.getSavedDistributor(activity)
        val distributors = UnifiedPush.getDistributors(activity)
        Log.i("PushProvider", "ensureRegistered ack=$ack saved=$saved distributors=$distributors")
        if (ack != null) return
        val distributor = distributors.firstOrNull() ?: return
        UnifiedPush.saveDistributor(activity, distributor)
        UnifiedPush.register(activity)
        Log.i("PushProvider", "register requested via $distributor")
    }
}
