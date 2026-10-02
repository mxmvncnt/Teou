package com.mxmvncnt.teou.push

import android.app.Activity
import android.content.Context
import android.util.Log
import com.mxmvncnt.teou.push.unifiedpush.UnifiedPushStore
import org.unifiedpush.android.connector.UnifiedPush

object PushProvider {

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
