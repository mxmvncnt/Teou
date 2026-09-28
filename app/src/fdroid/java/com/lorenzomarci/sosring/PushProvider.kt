package com.lorenzomarci.sosring

import android.app.Activity
import android.content.Context
import android.util.Log
import org.unifiedpush.android.connector.UnifiedPush

object PushProvider {

    fun requestLocation(context: Context, contact: VipContact): Boolean {
        val peer = PeerStore(context).get(contact.number) ?: return false
        Thread { P2pMessaging.requestLocation(context.applicationContext, peer) }.start()
        return true
    }

    fun canRequestLocation(context: Context, number: String): Boolean {
        val registered = !UnifiedPushStore(context).endpointUrl.isNullOrBlank()
        val peerPaired = PeerStore(context).get(number) != null
        return P2pLocationReadiness.check(registered, peerPaired) == P2pBlock.NONE
    }

    fun locationBlock(context: Context, contact: VipContact): String? {
        val registered = !UnifiedPushStore(context).endpointUrl.isNullOrBlank()
        val peerPaired = PeerStore(context).get(contact.number) != null
        return when (P2pLocationReadiness.check(registered, peerPaired)) {
            P2pBlock.NONE -> null
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
