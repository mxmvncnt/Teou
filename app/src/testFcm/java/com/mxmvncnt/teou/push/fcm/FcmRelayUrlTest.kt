package com.mxmvncnt.teou.push.fcm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FcmRelayUrlTest {
    @Test fun acceptsHttpsAndRejectsUnsafeOrMalformedAddresses() {
        assertEquals("https://example.com/", FcmStore.normalizeRelayUrl(" https://EXAMPLE.com:443 "))
        assertEquals("https://example.com/fcm/", FcmStore.normalizeRelayUrl("https://example.com/fcm/"))
        for (input in listOf("", "not a URL", "https://", "http://example.com",
                "https://user:secret@example.com", "https://example.com?token=secret", "https://example.com#fragment")) {
            assertNull(input, FcmStore.normalizeRelayUrl(input))
        }
    }
}
