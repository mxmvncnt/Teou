package com.mxmvncnt.teou.push.fcm

import com.google.firebase.messaging.FirebaseMessagingService

class FcmMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        FcmStore(this).token = token
    }
}
