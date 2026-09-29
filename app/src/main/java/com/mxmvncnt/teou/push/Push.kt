package com.mxmvncnt.teou.push

import android.app.Activity
import android.content.Context
import com.mxmvncnt.teou.*
import com.mxmvncnt.teou.data.VipContact
import com.mxmvncnt.teou.push.unifiedpush.PushProvider

object Push {
    fun locationBlock(context: Context, contact: VipContact): String? = PushProvider.locationBlock(context, contact)
    fun ensureRegistered(activity: Activity) = PushProvider.ensureRegistered(activity)

    fun requestLocation(context: Context, contact: VipContact): Boolean =
        PushProvider.requestLocation(context, contact)

    fun canRequestLocation(context: Context, number: String): Boolean =
        PushProvider.canRequestLocation(context, number)

}
