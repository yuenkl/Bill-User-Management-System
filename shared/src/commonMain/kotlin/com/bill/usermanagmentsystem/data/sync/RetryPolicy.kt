package com.bill.usermanagmentsystem.data.sync

import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

internal class RetryPolicy(
    private val initialDelayMilliseconds: Long = 2_000,
    private val maximumDelayMilliseconds: Long = 300_000,
) {
    init {
        require(initialDelayMilliseconds > 0) { "Initial retry delay must be positive." }
        require(maximumDelayMilliseconds >= initialDelayMilliseconds) {
            "Maximum retry delay must not be shorter than the initial delay."
        }
    }

    fun nextRetryAt(
        now: Instant,
        previousAttemptCount: Long,
        serverRetryAt: Instant?,
    ): Instant {
        val exponent = previousAttemptCount.coerceIn(0, MAX_SAFE_EXPONENT.toLong()).toInt()
        val multiplier = 1L shl exponent
        val localDelay = if (initialDelayMilliseconds > maximumDelayMilliseconds / multiplier) {
            maximumDelayMilliseconds
        } else {
            min(initialDelayMilliseconds * multiplier, maximumDelayMilliseconds)
        }
        val localRetryAt = now + localDelay.milliseconds
        return if (serverRetryAt != null && serverRetryAt > localRetryAt) {
            serverRetryAt
        } else {
            localRetryAt
        }
    }

    private companion object {
        const val MAX_SAFE_EXPONENT = 20
    }
}
