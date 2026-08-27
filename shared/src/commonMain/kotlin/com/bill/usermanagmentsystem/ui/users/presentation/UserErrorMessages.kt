package com.bill.usermanagmentsystem.ui.users.presentation

import com.bill.usermanagmentsystem.domain.model.UserDataError
import com.bill.usermanagmentsystem.domain.model.userDataErrorOrNull

internal fun Throwable?.toUserMessage(): String =
    this?.userDataErrorOrNull()?.toUserMessage() ?: "The user directory could not be refreshed."

internal fun UserDataError.toUserMessage(): String =
    when (this) {
        UserDataError.Offline -> "You're offline. Cached users will remain available."
        UserDataError.AuthenticationRequired -> "Check the GoRest access token, then retry."
        UserDataError.DeleteTooLate -> "That deletion can no longer be undone."
        is UserDataError.UserNotFound -> "That user is no longer available."
        is UserDataError.ValidationRejected -> reason
        is UserDataError.RetryScheduled -> reason
        is UserDataError.Persistence -> "Saved users could not be read. $reason"
        is UserDataError.RemoteContract -> "The service returned unexpected data. $reason"
        is UserDataError.Unexpected -> reason
    }
