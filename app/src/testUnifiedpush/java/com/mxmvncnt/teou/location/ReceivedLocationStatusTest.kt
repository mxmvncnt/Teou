package com.mxmvncnt.teou.location

import com.mxmvncnt.teou.messaging.Peer
import com.mxmvncnt.teou.messaging.PeerStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceivedLocationStatusTest {
    @Test fun lastSuccessfulFixTurnsGreyOnlyAfterAnUnansweredRequest() {
        assertFalse(unansweredRequest(0L, 1_000L, 100_000L))
        assertFalse(unansweredRequest(2_000L, 1_000L, 61_999L))
        assertTrue(unansweredRequest(2_000L, 1_000L, 62_000L))
        assertFalse(unansweredRequest(2_000L, 3_000L, 100_000L))
    }
}
