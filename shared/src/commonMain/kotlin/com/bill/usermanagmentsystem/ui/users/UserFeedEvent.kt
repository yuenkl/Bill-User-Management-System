package com.bill.usermanagmentsystem.ui.users

import com.bill.usermanagmentsystem.domain.model.AddUserInput

sealed interface UserFeedEvent {
    data object ScrollToTop : UserFeedEvent

    data class ShowSnackbar(
        val message: String,
    ) : UserFeedEvent

    data class ShowDeleteUndoSnackbar(
        val userName: String,
        val input: AddUserInput,
    ) : UserFeedEvent
}
