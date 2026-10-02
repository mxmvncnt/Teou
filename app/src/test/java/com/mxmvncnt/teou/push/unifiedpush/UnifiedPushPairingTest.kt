package com.mxmvncnt.teou.push.unifiedpush

import com.mxmvncnt.teou.messaging.WebPushCrypto
import com.mxmvncnt.teou.messaging.PushTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnifiedPushPairingTest {

    @Test
    fun decode_legacyPairingDefaultsToUnifiedPush() {
        val legacy = encodedJson("""{"e":"https://push.example/up","p":"key","a":"secret","i":"identity"}""")
        assertEquals(PairPayload("https://push.example/up", "key", "secret", "identity"), UnifiedPushPairing.decode(legacy))
    }

    @Test
    fun roundTrip_fcmIncludesRecipientAndKeys() {
        val payload = PairPayload("", "key", "secret", "identity", PushTransport.FCM, "https://relay.example/fcm/", "recipient")
        assertEquals(payload, UnifiedPushPairing.decode(UnifiedPushPairing.encode(payload)))
    }

    @Test
    fun decode_rejectsUnknownTransportAndIncompleteFcmAddress() {
        for (json in listOf(
            """{"transport":"sms","e":"https://push.example/up","p":"key","a":"secret"}""",
            """{"transport":"fcm","relayUrl":"https://relay.example/","p":"key","a":"secret"}""",
            """{"transport":"fcm","token":"recipient","p":"key","a":"secret"}"""
        )) assertNull(UnifiedPushPairing.decode(encodedJson(json)))
    }

    private fun encodedJson(json: String) = "teou1:" + WebPushCrypto.b64enc(json.toByteArray())

    @Test
    fun roundTrip_withKeyFingerprint() {
        val payload = PairPayload(
            endpoint = "https://example.com/upABC123",
            p256dh = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4",
            auth = "BTBZMqHH6r4Tts7J_aSIgg",
            idPub = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4"
        )

        val decoded = UnifiedPushPairing.decode(UnifiedPushPairing.encode(payload))

        assertEquals(payload, decoded)
    }

    @Test
    fun roundTrip_withoutIdentityKey() {
        val payload = PairPayload(
            endpoint = "https://push.example.com/up1",
            p256dh = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8",
            auth = "DGv6ra1nlYgDCS1FRnbzlw"
        )

        val decoded = UnifiedPushPairing.decode(UnifiedPushPairing.encode(payload))

        assertEquals(payload, decoded)
        assertNull(decoded?.idPub)
    }

    @Test
    fun decode_rejectsWrongPrefix() {
        assertNull(UnifiedPushPairing.decode("https://push.example.com/up1"))
    }

    @Test
    fun decode_rejectsGarbage() {
        assertNull(UnifiedPushPairing.decode("teou1:not-valid-base64-or-json!!!"))
    }

    @Test
    fun decode_rejectsMissingRequiredField() {
        val incomplete = "teou1:" + WebPushCrypto.b64enc("""{"e":"https://x/up1","p":"key"}""".toByteArray())
        assertNull(UnifiedPushPairing.decode(incomplete))
    }
}
