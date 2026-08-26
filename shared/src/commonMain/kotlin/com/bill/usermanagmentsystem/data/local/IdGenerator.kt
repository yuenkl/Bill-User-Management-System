package com.bill.usermanagmentsystem.data.local

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun interface IdGenerator {
    fun nextId(): String
}

@OptIn(ExperimentalUuidApi::class)
class UuidIdGenerator : IdGenerator {
    override fun nextId(): String = Uuid.random().toString()
}
