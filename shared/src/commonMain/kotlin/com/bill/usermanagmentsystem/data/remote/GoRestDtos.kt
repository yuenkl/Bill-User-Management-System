package com.bill.usermanagmentsystem.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GoRestUserDto(
    val id: Long,
    val name: String,
    val email: String,
    val gender: String,
    val status: String,
)

@Serializable
internal data class GoRestCreateUserDto(
    val name: String,
    val email: String,
    val gender: String,
    val status: String,
)

@Serializable
internal data class GoRestFieldErrorDto(
    val field: String,
    val message: String,
)

@Serializable
internal data class GoRestMessageErrorDto(
    @SerialName("message") val message: String,
)
