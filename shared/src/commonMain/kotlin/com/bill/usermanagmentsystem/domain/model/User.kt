package com.bill.usermanagmentsystem.domain.model

import kotlin.time.Instant

data class User(
    val localId: String,
    val remoteId: Long?,
    val name: String,
    val email: String,
    val gender: Gender,
    val status: UserStatus,
    val observedAt: Instant,
)

data class AddUserInput(
    val name: String,
    val email: String,
    val gender: Gender,
    val status: UserStatus,
)

enum class Gender(val apiValue: String) {
    Female("female"),
    Male("male"),
}

enum class UserStatus(val apiValue: String) {
    Active("active"),
    Inactive("inactive"),
}

data class UserRecord(
    val user: User,
    val synchronization: UserSynchronization,
)

data class UndoableDeletion(
    val user: User,
    val deadline: Instant,
)

sealed interface UserSynchronization {
    data object Synced : UserSynchronization
    data object PendingCreate : UserSynchronization
    data class CreateFailed(val reason: String) : UserSynchronization
}
