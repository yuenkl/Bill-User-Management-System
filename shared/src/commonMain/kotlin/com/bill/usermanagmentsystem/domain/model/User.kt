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

data class DeletedUserUndo(
    val userName: String,
    val input: AddUserInput,
)

enum class Gender(
    val apiValue: String,
) {
    Female("female"),
    Male("male"),
}

enum class UserStatus(
    val apiValue: String,
) {
    Active("active"),
    Inactive("inactive"),
}

data class UserRecord(
    val user: User,
)
