package com.bill.usermanagmentsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.UserStatus
import com.bill.usermanagmentsystem.ui.theme.UserManagementTheme
import com.bill.usermanagmentsystem.ui.users.UserFeedBanner
import com.bill.usermanagmentsystem.ui.users.UserFeedEmptyState
import com.bill.usermanagmentsystem.ui.users.UserFeedRoute
import com.bill.usermanagmentsystem.ui.users.UserFeedScreen
import com.bill.usermanagmentsystem.ui.users.UserFeedUiState
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
    PreviewFeed(
        state = previewFeedState(),
        darkTheme = false,
    )
}

@Preview
@Composable
private fun LoadingPreview() {
    PreviewFeed(UserFeedUiState(initialLoading = true))
}

@Preview
@Composable
private fun EmptyPreview() {
    PreviewFeed(
        UserFeedUiState(
            initialLoading = false,
            emptyState = UserFeedEmptyState.Empty,
        ),
    )
}

@Preview
@Composable
private fun OfflinePreview() {
    PreviewFeed(
        previewFeedState().copy(banner = UserFeedBanner.Offline),
    )
}

@Preview
@Composable
private fun ErrorPreview() {
    PreviewFeed(
        UserFeedUiState(
            initialLoading = false,
            emptyState = UserFeedEmptyState.Error("The service returned malformed data."),
        ),
    )
}

@Composable
private fun PreviewFeed(
    state: UserFeedUiState,
    darkTheme: Boolean = false,
) {
    UserManagementTheme(darkTheme = darkTheme) {
        UserFeedScreen(
            state = state,
            onAddUser = {},
            onAddUserDismissed = {},
            onAddUserNameChanged = {},
            onAddUserEmailChanged = {},
            onAddUserGenderSelected = {},
            onAddUserStatusSelected = {},
            onAddUserSubmitted = {},
        )
    }
}

private fun previewFeedState() =
    UserFeedUiState(
        users = listOf(previewUser()),
        initialLoading = false,
    )

private fun previewUser(
    localId: String = "preview-user",
    name: String = "Ada Lovelace",
) = UserItemUiModel(
    localId = localId,
    name = name,
    email = "${name.substringBefore(' ').lowercase()}@example.com",
    gender = Gender.Female,
    status = UserStatus.Active,
    relativeTime = "2 minutes ago",
)
