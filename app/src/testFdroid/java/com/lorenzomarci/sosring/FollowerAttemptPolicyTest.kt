package com.lorenzomarci.sosring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowerAttemptPolicyTest {

    @Test fun firstAttemptIsAlwaysRecorded() {
        assertTrue(FollowerAttemptPolicy.shouldRecord(0L, 1_000L))
    }

    @Test fun repeatAttemptInsideThrottleWindowIsSkipped() {
        val interval = FollowerAttemptPolicy.MIN_RECORD_INTERVAL_MS
        assertFalse(FollowerAttemptPolicy.shouldRecord(10_000L, 10_000L + interval - 1))
    }

    @Test fun repeatAttemptAfterThrottleWindowIsRecorded() {
        val interval = FollowerAttemptPolicy.MIN_RECORD_INTERVAL_MS
        assertTrue(FollowerAttemptPolicy.shouldRecord(10_000L, 10_000L + interval))
    }
}
