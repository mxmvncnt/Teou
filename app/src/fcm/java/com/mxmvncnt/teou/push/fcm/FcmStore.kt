package com.mxmvncnt.teou.push.fcm

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class FcmStore(context: Context) {
    private val prefs = context.getSharedPreferences("teou_prefs", Context.MODE_PRIVATE)

    var relayUrl: String?
        get() = prefs.getString("fcm_relay_url", "https://fcm.mxm.vc")?.takeIf { it.isNotEmpty() }
        set(value) = prefs.edit().putString("fcm_relay_url", value.orEmpty()).apply()

    var token: String?
        get() = prefs.getString("fcm_token", null)
        set(value) = prefs.edit().putString("fcm_token", value).apply()

    companion object {
        fun normalizeRelayUrl(value: String): String? = value.trim().toHttpUrlOrNull()
            ?.takeIf { it.isHttps && it.username.isEmpty() && it.password.isEmpty() &&
                it.query == null && it.fragment == null }
            ?.toString()
    }
}
