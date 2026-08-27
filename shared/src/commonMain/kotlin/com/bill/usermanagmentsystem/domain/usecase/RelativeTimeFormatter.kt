package com.bill.usermanagmentsystem.domain.usecase

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class RelativeTimeFormatter {
    fun format(
        observedAt: Instant,
        now: Instant,
    ): String {
        val elapsed = now - observedAt
        if (elapsed.isNegative() || elapsed < 1.minutes) return "Just now"

        val minutes = elapsed.inWholeMinutes
        if (elapsed < 1.hours) return quantity(minutes, "minute")

        val hours = elapsed.inWholeHours
        if (elapsed < 1.days) return quantity(hours, "hour")

        val days = elapsed.inWholeDays
        if (elapsed < 7.days) return quantity(days, "day")

        return observedAt.toString().take(10)
    }

    private fun quantity(
        value: Long,
        unit: String,
    ): String = "$value $unit${if (value == 1L) "" else "s"} ago"
}
