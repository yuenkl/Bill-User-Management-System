package com.bill.usermanagmentsystem.ui.users

import com.bill.usermanagmentsystem.domain.model.UserDataError
import com.bill.usermanagmentsystem.domain.model.UserRecord
import com.bill.usermanagmentsystem.domain.model.userDataErrorOrNull
import com.bill.usermanagmentsystem.domain.usecase.RelativeTimeFormatter
import com.bill.usermanagmentsystem.platform.ConnectivityStatus
import kotlin.time.Instant

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

internal fun buildUserFeedUiState(
    users: List<UserRecord>,
    connectivity: ConnectivityStatus,
    now: Instant,
    presentation: UserFeedPresentationState,
    relativeTimeFormatter: RelativeTimeFormatter,
): UserFeedUiState {
    val items = users.map { it.toUiModel(now, relativeTimeFormatter) }
    val offline = connectivity == ConnectivityStatus.Unavailable
    val error = presentation.refreshError
    val initialLoading = items.isEmpty() && !presentation.initialAttemptFinished
    val emptyState =
        when {
            items.isNotEmpty() || initialLoading -> null
            offline -> UserFeedEmptyState.Offline
            error == UserDataError.AuthenticationRequired -> UserFeedEmptyState.AuthenticationRequired
            error != null -> UserFeedEmptyState.Error(error.toUserMessage())
            else -> UserFeedEmptyState.Empty
        }
    val banner =
        when {
            items.isEmpty() -> null
            offline -> UserFeedBanner.Offline
            error == UserDataError.AuthenticationRequired -> UserFeedBanner.AuthenticationRequired
            error != null -> UserFeedBanner.RefreshFailed(error.toUserMessage())
            else -> null
        }
    return UserFeedUiState(
        users = items,
        initialLoading = initialLoading,
        refreshing = presentation.refreshing,
        loadingMore = presentation.loadingNextPage,
        canLoadMore = presentation.canLoadNextPage,
        loadMoreError = presentation.nextPageError,
        emptyState = emptyState,
        banner = banner,
        addUserForm = presentation.addUserForm,
        addUserValidationAlert = presentation.addUserValidationAlert,
        deleteConfirmation = items.firstOrNull { it.localId == presentation.selectedUserId },
        deleteInProgress = presentation.deleteInProgress,
    )
}

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

private fun UserRecord.toUiModel(
    now: Instant,
    relativeTimeFormatter: RelativeTimeFormatter,
): UserItemUiModel =
    UserItemUiModel(
        localId = user.localId,
        name = user.name,
        email = user.email,
        gender = user.gender,
        status = user.status,
        relativeTime = relativeTimeFormatter.format(user.observedAt, now),
    )
