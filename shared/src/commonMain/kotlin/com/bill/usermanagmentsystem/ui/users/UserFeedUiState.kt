package com.bill.usermanagmentsystem.ui.users

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
    val addUserForm: AddUserFormUiState? = null,
    val deleteConfirmation: UserItemUiModel? = null,
    val deleteInProgress: Boolean = false,
)

data class AddUserFormUiState(
    val details: List<AddUserFormEntry> = defaultAddUserFormEntries(),
    val touchedFields: Set<AddUserFormEntryType> = emptySet(),
    val isValid: Boolean = false,
    val submitting: Boolean = false,
) {
    fun valueFor(field: AddUserFormEntryType): String? = details.firstOrNull { it.type == field }?.value

    fun errorMessage(field: AddUserFormEntryType): String? = details.firstOrNull { it.type == field }?.error

    fun gender(): Gender? = Gender.entries.firstOrNull { it.apiValue == valueFor(AddUserFormEntryType.Gender) }

    fun status(): UserStatus =
        UserStatus.entries.firstOrNull { it.apiValue == valueFor(AddUserFormEntryType.Status) }
            ?: UserStatus.Active

    val canSubmit: Boolean
        get() = isValid && details.none { it.type != AddUserFormEntryType.Form && it.error != null } && !submitting
}

enum class AddUserFormEntryType {
    Name,
    Email,
    Gender,
    Status,
    Form,
    ;

    companion object {
        fun fromApiName(value: String): AddUserFormEntryType? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) && it != Form }
    }
}

data class AddUserFormEntry(
    val type: AddUserFormEntryType,
    val value: String? = null,
    val error: String? = null,
)

private fun defaultAddUserFormEntries(): List<AddUserFormEntry> =
    listOf(
        AddUserFormEntry(AddUserFormEntryType.Name),
        AddUserFormEntry(AddUserFormEntryType.Email),
        AddUserFormEntry(AddUserFormEntryType.Gender),
        AddUserFormEntry(AddUserFormEntryType.Status, value = UserStatus.Active.apiValue),
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
)

sealed interface UserFeedEmptyState {
    data object Empty : UserFeedEmptyState

    data object Offline : UserFeedEmptyState

    data object AuthenticationRequired : UserFeedEmptyState

    data class Error(
        val message: String,
    ) : UserFeedEmptyState
}

sealed interface UserFeedBanner {
    data object Offline : UserFeedBanner

    data object AuthenticationRequired : UserFeedBanner

    data class RefreshFailed(
        val message: String,
    ) : UserFeedBanner
}
