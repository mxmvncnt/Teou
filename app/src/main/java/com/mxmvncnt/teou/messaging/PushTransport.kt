package com.mxmvncnt.teou.messaging

enum class PushTransport(val wireName: String) {
    UNIFIED_PUSH("unifiedpush"),
    FCM("fcm");

    companion object {
        // Pairings and peers saved before transports were explicit used UnifiedPush.
        fun fromWireName(value: String): PushTransport? = entries.firstOrNull { it.wireName == value }
    }
}
