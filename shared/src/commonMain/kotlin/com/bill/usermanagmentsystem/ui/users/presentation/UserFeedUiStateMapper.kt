package com.bill.usermanagmentsystem.ui.users.presentation

import com.bill.usermanagmentsystem.domain.model.UserDataError
import com.bill.usermanagmentsystem.domain.model.UserRecord
import com.bill.usermanagmentsystem.platform.ConnectivityStatus
import com.bill.usermanagmentsystem.ui.users.UserFeedBanner
import com.bill.usermanagmentsystem.ui.users.UserFeedEmptyState
import com.bill.usermanagmentsystem.ui.users.UserFeedUiState
import com.bill.usermanagmentsystem.ui.users.UserItemUiModel
import com.bill.usermanagmentsystem.utils.RelativeTimeFormatter
import kotlin.time.Instant

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
        deleteConfirmation = items.firstOrNull { it.localId == presentation.selectedUserId },
        deleteInProgress = presentation.deleteInProgress,
    )
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
