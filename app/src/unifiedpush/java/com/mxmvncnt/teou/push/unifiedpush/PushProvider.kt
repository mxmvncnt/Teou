package com.mxmvncnt.teou.push.unifiedpush

import android.app.Activity
import android.content.Context
import android.util.Log
import com.mxmvncnt.teou.R
import com.mxmvncnt.teou.data.VipContact
import com.mxmvncnt.teou.messaging.*
import com.mxmvncnt.teou.location.ReceivedLocationStore
import org.unifiedpush.android.connector.UnifiedPush

object PushProvider {

    @Synchronized
    fun requestLocation(context: Context, contact: VipContact): Boolean {
        if (locationBlock(context, contact) != null) return false
        val peer = PeerStore(context).get(contact.number) ?: return false
        ReceivedLocationStore(context).requested(peer)
        Thread { P2pMessaging.requestLocation(context.applicationContext, peer) }.start()
        return true
    }

    fun canRequestLocation(context: Context, number: String): Boolean = locationBlock(context, number) == null

    fun locationBlock(context: Context, contact: VipContact): String? = locationBlock(context, contact.number)

    private fun locationBlock(context: Context, number: String): String? {
        val registered = !UnifiedPushStore(context).endpointUrl.isNullOrBlank()
        val peer = PeerStore(context).get(number)
        return when (P2pLocationReadiness.check(registered, peer != null)) {
            P2pBlock.NONE -> {
                val remaining = peer?.let { ReceivedLocationStore(context).requestCooldownRemaining(it) } ?: 0L
                if (remaining > 0L) context.getString(R.string.location_request_cooldown, (remaining + 999L) / 1_000L)
                else null
            }
            P2pBlock.NOT_REGISTERED -> context.getString(R.string.p2p_block_not_registered)
            P2pBlock.NOT_PAIRED -> context.getString(R.string.p2p_block_not_paired)
        }
    }

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
