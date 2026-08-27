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
    val details: List<UserDetail> = defaultAddUserDetails(),
    val touchedFields: Set<AddUserField> = emptySet(),
    val isValid: Boolean = false,
    val submitting: Boolean = false,
) {
    fun valueFor(field: AddUserField): String? = details.firstOrNull { it.type == field }?.value

    fun errorMessage(field: AddUserField): String? = details.firstOrNull { it.type == field }?.error

    fun gender(): Gender? =
        Gender.entries.firstOrNull { it.apiValue == valueFor(AddUserField.Gender) }

    fun status(): UserStatus =
        UserStatus.entries.firstOrNull { it.apiValue == valueFor(AddUserField.Status) }
            ?: UserStatus.Active

    val canSubmit: Boolean
        get() = isValid && details.none { it.type != AddUserField.Form && it.error != null } && !submitting
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

data class UserDetail(
    val type: AddUserField,
    val value: String? = null,
    val error: String? = null,
)

private fun defaultAddUserDetails(): List<UserDetail> = listOf(
    UserDetail(AddUserField.Name),
    UserDetail(AddUserField.Email),
    UserDetail(AddUserField.Gender),
    UserDetail(AddUserField.Status, value = UserStatus.Active.apiValue),
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
