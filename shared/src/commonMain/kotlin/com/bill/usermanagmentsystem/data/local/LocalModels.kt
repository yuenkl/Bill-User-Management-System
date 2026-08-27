package com.bill.usermanagmentsystem.data.local

import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.UserStatus
import kotlin.time.Instant

internal data class StoredUser(
    val localId: String,
    val remoteId: Long?,
    val name: String,
    val email: String,
    val gender: Gender,
    val status: UserStatus,
    val observedAt: Instant,
    val serverPosition: Long?,
)

internal data class SnapshotUser(
    val remoteId: Long,
    val name: String,
    val email: String,
    val gender: Gender,
    val status: UserStatus,
    val serverPosition: Long?,
)
