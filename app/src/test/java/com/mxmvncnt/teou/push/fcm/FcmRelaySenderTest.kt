package com.mxmvncnt.teou.push.fcm

import com.mxmvncnt.teou.messaging.WebPushCrypto
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FcmRelaySenderTest {
    @Test fun postsBase64UrlPayloadAndTokenToSendUnderRelayBasePath() {
        val body = byteArrayOf(-1, -2, -3, 0)
        for (base in listOf("https://relay.example/fcm", "https://relay.example/fcm/")) {
            val request = FcmRelaySender.buildRequest(base, "recipient-token", body)
            assertEquals("https://relay.example/fcm/send", request.url.toString())
            assertEquals("POST", request.method)
            assertEquals("application/json; charset=utf-8", request.body!!.contentType().toString())
            val buffer = Buffer()
            request.body!!.writeTo(buffer)
            val json = JSONObject(buffer.readUtf8())
            assertEquals("recipient-token", json.getString("token"))
            assertArrayEquals(body, WebPushCrypto.b64dec(json.getString("payload")))
            assertNull(request.header("Content-Encoding"))
        }
    }

    @Test fun rejectsUnsafeRelaysMissingTokensAndOversizedPayloads() {
        for (url in listOf("http://relay.example", "https://user:secret@relay.example", "https://relay.example?key=secret")) {
            assertThrows(IllegalArgumentException::class.java) { FcmRelaySender.buildRequest(url, "token", byteArrayOf(1)) }
        }
        assertThrows(IllegalArgumentException::class.java) { FcmRelaySender.buildRequest("https://relay.example", " ", byteArrayOf(1)) }
        assertThrows(IllegalArgumentException::class.java) { FcmRelaySender.buildRequest("https://relay.example", "token", ByteArray(4096)) }
    }
}
