package com.lorenzomarci.sosring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (Push.canStart(context)) {
                Log.i("SOSRing", "Boot: restarting push engine")
                Push.start(context)
            }
        }
    }
}
