package com.bill.usermanagmentsystem.platform

import kotlin.time.Clock
import kotlin.time.Instant

interface TimeProvider {
    fun now(): Instant
}

class SystemTimeProvider : TimeProvider {
    override fun now(): Instant = Clock.System.now()
}
