package com.bill.usermanagmentsystem.data.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class RetryPolicyTest {
    private val policy = RetryPolicy()
    private val now = Instant.fromEpochMilliseconds(1_000)

    @Test
    fun backoffStartsAtTwoSecondsAndDoubles() {
        assertEquals(instant(3_000), policy.nextRetryAt(now, 0, null))
        assertEquals(instant(5_000), policy.nextRetryAt(now, 1, null))
        assertEquals(instant(9_000), policy.nextRetryAt(now, 2, null))
    }

    @Test
    fun backoffCapsAtFiveMinutesWithoutOverflow() {
        assertEquals(instant(301_000), policy.nextRetryAt(now, 100, null))
    }

    @Test
    fun laterServerRetryTimeWins() {
        assertEquals(
            instant(31_000),
            policy.nextRetryAt(now, 0, serverRetryAt = instant(31_000)),
        )
    }

    @Test
    fun earlierServerRetryTimeCannotShortenBackoff() {
        assertEquals(
            instant(3_000),
            policy.nextRetryAt(now, 0, serverRetryAt = instant(2_000)),
        )
    }

    private fun instant(value: Long) = Instant.fromEpochMilliseconds(value)
}
