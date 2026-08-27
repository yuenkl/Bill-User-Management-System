package com.bill.usermanagmentsystem.domain.usecase

import com.bill.usermanagmentsystem.utils.RelativeTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RelativeTimeFormatterTest {
    private val formatter = RelativeTimeFormatter()
    private val now = Instant.parse("2026-08-26T12:00:00Z")

    @Test
    fun justNowIncludesFutureAndFirstMinute() {
        assertEquals("Just now", formatter.format(now + 5.minutes, now))
        assertEquals("Just now", formatter.format(now - 59.seconds, now))
    }

    @Test
    fun minuteBoundariesUseCorrectPluralization() {
        assertEquals("1 minute ago", formatter.format(now - 1.minutes, now))
        assertEquals("59 minutes ago", formatter.format(now - 59.minutes, now))
    }

    @Test
    fun hourBoundariesUseCorrectPluralization() {
        assertEquals("1 hour ago", formatter.format(now - 1.hours, now))
        assertEquals("23 hours ago", formatter.format(now - 23.hours, now))
    }

    @Test
    fun dayBoundariesThenFallBackToStableDate() {
        assertEquals("1 day ago", formatter.format(now - 1.days, now))
        assertEquals("6 days ago", formatter.format(now - 6.days, now))
        assertEquals("2026-08-19", formatter.format(now - 7.days, now))
    }
}
