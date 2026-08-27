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
    val addUserValidationAlert: AddUserValidationAlert? = null,
    val deleteConfirmation: UserItemUiModel? = null,
    val deleteInProgress: Boolean = false,
    val undoSnackbar: DeleteUndoUiModel? = null,
)

data class AddUserFormUiState(
    val name: String = "",
    val email: String = "",
    val gender: Gender? = null,
    val status: UserStatus = UserStatus.Active,
    val touchedFields: Set<AddUserField> = emptySet(),
    val errors: List<UserDetail> = emptyList(),
    val isValid: Boolean = false,
    val submitting: Boolean = false,
) {
    fun errorMessage(field: AddUserField): String? =
        errors
            .filter { it.type == field }
            .minByOrNull { it.source.priority }
            ?.error

    val canSubmit: Boolean
        get() = isValid && errors.none { it.source == AddUserErrorSource.Api } && !submitting
}

enum class AddUserField {
    Name,
    Email,
    Gender,
    Status,
    Form,
    ;

    companion object {
        fun fromApiName(value: String): AddUserField? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) && it != Form }
    }
}

enum class AddUserErrorSource(val priority: Int) {
    Validation(priority = 0),
    Api(priority = 1),
    Submission(priority = 2),
}

data class UserDetail(
    val type: AddUserField,
    val error: String? = null,
    val source: AddUserErrorSource = AddUserErrorSource.Validation,
)

data class AddUserValidationAlert(
    val errors: List<AddUserApiFieldError>,
)

data class AddUserApiFieldError(
    val field: String,
    val message: String,
)

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
