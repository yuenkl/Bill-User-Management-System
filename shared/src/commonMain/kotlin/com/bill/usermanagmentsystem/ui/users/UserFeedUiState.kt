package com.bill.usermanagmentsystem.ui.users

import com.bill.usermanagmentsystem.domain.model.AddUserInput
import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.UserStatus

data class UserFeedUiState(
    val users: List<UserItemUiModel> = emptyList(),
    val initialLoading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val loadMoreError: String? = null,
    val emptyState: UserFeedEmptyState? = null,
    val banner: UserFeedBanner? = null,
    val message: UserFeedMessage? = null,
    val addUserForm: AddUserFormUiState? = null,
    val deleteConfirmation: UserItemUiModel? = null,
    val deleteInProgress: Boolean = false,
    val undoSnackbar: DeleteUndoUiModel? = null,
)

data class AddUserFormUiState(
    val name: String = "",
    val email: String = "",
    val gender: Gender? = null,
    val status: UserStatus = UserStatus.Active,
    val nameTouched: Boolean = false,
    val emailTouched: Boolean = false,
    val genderTouched: Boolean = false,
    val nameError: String? = null,
    val emailError: String? = null,
    val genderError: String? = null,
    val nameApiError: String? = null,
    val emailApiError: String? = null,
    val submissionError: String? = null,
    val isValid: Boolean = false,
    val submitting: Boolean = false,
) {
    val canSubmit: Boolean
        get() = isValid && nameApiError == null && emailApiError == null && !submitting
}

enum class AddUserField {
    Name,
    Email,
}

data class UserItemUiModel(
    val localId: String,
    val name: String,
    val email: String,
    val gender: Gender,
    val status: UserStatus,
    val relativeTime: String,
    val synchronization: UserItemSynchronization,
)

sealed interface UserItemSynchronization {
    data object Synced : UserItemSynchronization
    data object Pending : UserItemSynchronization
    data class Failed(
        val reason: String,
        val retrying: Boolean = false,
    ) : UserItemSynchronization
}

sealed interface UserFeedEmptyState {
    data object Empty : UserFeedEmptyState
    data object Offline : UserFeedEmptyState
    data object AuthenticationRequired : UserFeedEmptyState
    data class Error(val message: String) : UserFeedEmptyState
}

sealed interface UserFeedBanner {
    data object Offline : UserFeedBanner
    data object AuthenticationRequired : UserFeedBanner
    data class RefreshFailed(val message: String) : UserFeedBanner
}

data class UserFeedMessage(
    val id: Long,
    val text: String,
)

data class DeleteUndoUiModel(
    val userName: String,
    val input: AddUserInput,
)
