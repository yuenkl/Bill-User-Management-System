package com.bill.usermanagmentsystem.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GoRestUserDto(
    val id: Long? = null,
    val name: String? = null,
    val email: String? = null,
    val gender: String? = null,
    val status: String? = null,
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
    val field: String? = null,
    val message: String? = null,
)

@Serializable
internal data class GoRestMessageErrorDto(
    @SerialName("message") val message: String? = null,
)
