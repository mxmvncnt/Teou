package com.mxmvncnt.teou

import android.app.Activity
import android.content.Context

object Push {
    fun locationBlock(context: Context, contact: VipContact): String? = PushProvider.locationBlock(context, contact)
    fun ensureRegistered(activity: Activity) = PushProvider.ensureRegistered(activity)

    fun requestLocation(context: Context, contact: VipContact): Boolean =
        PushProvider.requestLocation(context, contact)

    fun canRequestLocation(context: Context, number: String): Boolean =
        PushProvider.canRequestLocation(context, number)

}
