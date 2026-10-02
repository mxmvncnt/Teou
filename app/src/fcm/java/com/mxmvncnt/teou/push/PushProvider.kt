package com.mxmvncnt.teou.push

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.mxmvncnt.teou.push.fcm.FcmStore

object PushProvider {
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
