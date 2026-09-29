package com.mxmvncnt.teou.util

/** Bounded backoff for transient push-delivery failures, including location replies. */
object ControlRetryPolicy {
    val DELAYS_MS = longArrayOf(0L, 3_000L, 10_000L)
    val MAX_ATTEMPTS = DELAYS_MS.size

    fun delayBeforeAttempt(attempt: Int): Long =
        DELAYS_MS[attempt.coerceIn(0, DELAYS_MS.size - 1)]
}
