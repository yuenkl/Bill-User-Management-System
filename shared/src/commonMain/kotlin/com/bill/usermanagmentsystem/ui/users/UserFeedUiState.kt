package com.bill.usermanagmentsystem.ui.users

import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.UserStatus

data class UserFeedUiState(
    val users: List<UserItemUiModel> = emptyList(),
    val initialLoading: Boolean = true,
    val refreshing: Boolean = false,
    val emptyState: UserFeedEmptyState? = null,
    val banner: UserFeedBanner? = null,
    val message: UserFeedMessage? = null,
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
    data class Failed(val reason: String) : UserItemSynchronization
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
