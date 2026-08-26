package com.bill.usermanagmentsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.UserStatus
import com.bill.usermanagmentsystem.ui.theme.UserManagementTheme
import com.bill.usermanagmentsystem.ui.users.UserFeedRoute
import com.bill.usermanagmentsystem.ui.users.UserFeedScreen
import com.bill.usermanagmentsystem.ui.users.UserFeedUiState
import com.bill.usermanagmentsystem.ui.users.UserItemSynchronization
import com.bill.usermanagmentsystem.ui.users.UserItemUiModel

@Composable
fun App() {
    UserManagementTheme {
        UserFeedRoute()
    }
}

@Preview
@Composable
fun UserFeedPreview() {
    UserManagementTheme {
        UserFeedScreen(
            state = UserFeedUiState(
                users = listOf(
                    UserItemUiModel(
                        localId = "preview-user",
                        name = "Ada Lovelace",
                        email = "ada@example.com",
                        gender = Gender.Female,
                        status = UserStatus.Active,
                        relativeTime = "2 minutes ago",
                        synchronization = UserItemSynchronization.Synced,
                    ),
                ),
                initialLoading = false,
            ),
            onRefresh = {},
            onRetry = {},
            onAddUser = {},
            onAddUserDismissed = {},
            onAddUserNameChanged = {},
            onAddUserEmailChanged = {},
            onAddUserGenderSelected = {},
            onAddUserStatusSelected = {},
            onAddUserSubmitted = {},
            onRetryUserCreation = {},
            onMessageConsumed = {},
        )
    }
}
