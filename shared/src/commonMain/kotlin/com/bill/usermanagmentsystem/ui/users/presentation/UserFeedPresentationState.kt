package com.bill.usermanagmentsystem.ui.users.presentation

import com.bill.usermanagmentsystem.domain.model.UserDataError
import com.bill.usermanagmentsystem.ui.users.AddUserFormUiState
import com.bill.usermanagmentsystem.ui.users.AddUserValidationAlert

internal data class UserFeedPresentationState(
    val initialAttemptFinished: Boolean = false,
    val refreshing: Boolean = false,
    val loadingNextPage: Boolean = false,
    val canLoadNextPage: Boolean = false,
    val nextPageError: String? = null,
    val addUserForm: AddUserFormUiState? = null,
    val addUserValidationAlert: AddUserValidationAlert? = null,
    val refreshError: UserDataError? = null,
    val selectedUserId: String? = null,
    val deleteInProgress: Boolean = false,
)
