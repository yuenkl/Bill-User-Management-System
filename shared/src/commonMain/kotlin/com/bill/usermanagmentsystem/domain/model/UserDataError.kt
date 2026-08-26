package com.bill.usermanagmentsystem.domain.model

import kotlin.time.Instant

sealed interface UserDataError {
    data object Offline : UserDataError
    data object AuthenticationRequired : UserDataError
    data object DeleteTooLate : UserDataError
    data class UserNotFound(val localId: String) : UserDataError
    data class ValidationRejected(val reason: String) : UserDataError
    data class RetryScheduled(
        val reason: String,
        val retryAt: Instant,
    ) : UserDataError
    data class Persistence(val reason: String) : UserDataError
    data class RemoteContract(val reason: String) : UserDataError
    data class Unexpected(val reason: String) : UserDataError
}

class UserDataException(
    val error: UserDataError,
    cause: Throwable? = null,
) : Exception(error.message(), cause)

fun Throwable.userDataErrorOrNull(): UserDataError? = (this as? UserDataException)?.error

private fun UserDataError.message(): String = when (this) {
    UserDataError.Offline -> "No validated internet connection is available."
    UserDataError.AuthenticationRequired -> "Authentication is required before synchronization can continue."
    UserDataError.DeleteTooLate -> "The deletion can no longer be undone."
    is UserDataError.UserNotFound -> "No user exists for local ID $localId."
    is UserDataError.ValidationRejected -> reason
    is UserDataError.RetryScheduled -> "$reason Retry is scheduled for $retryAt."
    is UserDataError.Persistence -> reason
    is UserDataError.RemoteContract -> reason
    is UserDataError.Unexpected -> reason
}
