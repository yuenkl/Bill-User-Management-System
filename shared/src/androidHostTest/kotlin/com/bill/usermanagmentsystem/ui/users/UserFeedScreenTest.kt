package com.bill.usermanagmentsystem.ui.users

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.UserStatus
import com.bill.usermanagmentsystem.ui.theme.UserManagementTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class UserFeedScreenTest {
    @Test
    fun shimmerIsShownOnlyForInitialLoading() = runComposeUiTest {
        setContent { screen(UserFeedUiState(initialLoading = true)) }

        assertTrue(onAllNodesWithContentDescription("Loading users").fetchSemanticsNodes().isNotEmpty())
        assertTrue(onAllNodesWithText("No users yet").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun userCardExposesPrimaryIdentityAndTimestamp() = runComposeUiTest {
        setContent {
            screen(
                UserFeedUiState(
                    users = listOf(user()),
                    initialLoading = false,
                ),
            )
        }

        onNodeWithContentDescription(
            "Ada Lovelace, ada@example.com, 2 minutes ago",
        ).fetchSemanticsNode()
    }

    @Test
    fun emptyOfflineAndErrorStatesRemainDistinct() = runComposeUiTest {
        var state by mutableStateOf(UserFeedUiState(
            initialLoading = false,
            emptyState = UserFeedEmptyState.Empty,
        ))
        setContent { screen(state) }
        onNodeWithText("No users yet").fetchSemanticsNode()

        runOnIdle { state = state.copy(emptyState = UserFeedEmptyState.Offline) }
        onNodeWithText("You're offline").fetchSemanticsNode()

        runOnIdle {
            state = state.copy(emptyState = UserFeedEmptyState.Error("Malformed pagination"))
        }
        onNodeWithText("Couldn't load users").fetchSemanticsNode()
        onNodeWithText("Malformed pagination").fetchSemanticsNode()
    }

    @Test
    fun cachedOfflineFeedShowsBannerAndCard() = runComposeUiTest {
        setContent {
            screen(
                UserFeedUiState(
                    users = listOf(user()),
                    initialLoading = false,
                    banner = UserFeedBanner.Offline,
                ),
            )
        }

        onNodeWithText("Offline · showing saved users").fetchSemanticsNode()
        onNodeWithText("Ada Lovelace").fetchSemanticsNode()
    }

    @Test
    fun failedCreateOffersExplicitRetryForThatUser() = runComposeUiTest {
        var retriedId: String? = null
        setContent {
            UserManagementTheme {
                UserFeedScreen(
                    state = UserFeedUiState(
                        users = listOf(
                            user().copy(
                                synchronization = UserItemSynchronization.Failed(
                                    "email: already exists",
                                ),
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
                    onRetryUserCreation = { retriedId = it },
                    onMessageConsumed = {},
                )
            }
        }

        onNodeWithText("Retry sync").performClick()
        assertEquals("local-1", retriedId)
    }

    @Test
    fun retryAndAccessibleRefreshActionsForwardEvents() = runComposeUiTest {
        var retryCalls = 0
        var refreshCalls = 0
        setContent {
            UserManagementTheme {
                UserFeedScreen(
                    state = UserFeedUiState(
                        initialLoading = false,
                        emptyState = UserFeedEmptyState.Offline,
                    ),
                    onRefresh = { refreshCalls += 1 },
                    onRetry = { retryCalls += 1 },
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

        onNodeWithText("Retry").performClick()
        onNodeWithText("Refresh").performClick()

        assertEquals(1, retryCalls)
        assertEquals(1, refreshCalls)
    }

    @Test
    fun accessibleFabForwardsAddUserEvent() = runComposeUiTest {
        var addCalls = 0
        setContent {
            UserManagementTheme {
                UserFeedScreen(
                    state = UserFeedUiState(initialLoading = false),
                    onRefresh = {},
                    onRetry = {},
                    onAddUser = { addCalls += 1 },
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

        onNodeWithContentDescription("Add user").performClick()
        assertEquals(1, addCalls)
    }

    @Test
    @Config(qualifiers = "w800dp-h1000dp")
    fun compactFormUsesSheetAndRendersValues() = runComposeUiTest {
        val form = AddUserFormUiState(
            name = "Ada Lovelace",
            email = "ada@example.com",
            gender = Gender.Female,
            isValid = true,
        )
        setContent {
            Box(Modifier.size(width = 500.dp, height = 800.dp)) {
                screen(UserFeedUiState(initialLoading = false, addUserForm = form))
            }
        }

        onNodeWithContentDescription("Add user sheet").fetchSemanticsNode()
        onNodeWithText("Ada Lovelace").fetchSemanticsNode()
        onNodeWithText("ada@example.com").fetchSemanticsNode()
    }

    @Test
    fun adaptiveFormPresentationSwitchesAtSixHundredDp() {
        assertEquals(AddUserFormPresentation.Sheet, addUserFormPresentation(599.dp))
        assertEquals(AddUserFormPresentation.Dialog, addUserFormPresentation(600.dp))
    }

    @Test
    fun longClickOpensIdentityConfirmationAndCancelDoesNotDelete() = runComposeUiTest {
        var state by mutableStateOf(
            UserFeedUiState(
                users = listOf(user()),
                initialLoading = false,
            ),
        )
        var confirmCalls = 0
        setContent {
            UserManagementTheme {
                UserFeedScreen(
                    state = state,
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
                    onUserLongClick = { localId ->
                        state = state.copy(
                            deleteConfirmation = state.users.first { it.localId == localId },
                        )
                    },
                    onDeleteCancel = { state = state.copy(deleteConfirmation = null) },
                    onDeleteConfirm = { confirmCalls += 1 },
                )
            }
        }

        onNodeWithContentDescription(
            "Ada Lovelace, ada@example.com, 2 minutes ago",
        ).performSemanticsAction(SemanticsActions.OnLongClick)

        onNodeWithText("Delete user?").fetchSemanticsNode()
        assertEquals(2, onAllNodesWithText("Ada Lovelace").fetchSemanticsNodes().size)
        assertEquals(2, onAllNodesWithText("ada@example.com").fetchSemanticsNodes().size)
        onNodeWithText("Cancel").performClick()

        assertEquals(0, confirmCalls)
        assertTrue(onAllNodesWithText("Delete user?").fetchSemanticsNodes().isEmpty())

        onNodeWithContentDescription(
            "Ada Lovelace, ada@example.com, 2 minutes ago",
        ).performSemanticsAction(SemanticsActions.OnLongClick)
        onNodeWithText("Delete").performClick()
        assertEquals(1, confirmCalls)
    }

    @Test
    fun undoSnackbarNamesUserAndForwardsUndoAction() = runComposeUiTest {
        var undoLocalId: String? = null
        setContent {
            UserManagementTheme {
                UserFeedScreen(
                    state = UserFeedUiState(
                        initialLoading = false,
                        emptyState = UserFeedEmptyState.Empty,
                        undoSnackbar = DeleteUndoUiModel(
                            localId = "local-1",
                            userName = "Ada Lovelace",
                            deadline = Instant.parse("2026-08-26T12:00:05Z"),
                        ),
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
                    onUndoDelete = { undoLocalId = it },
                )
            }
        }

        onNodeWithText("Ada Lovelace deleted").fetchSemanticsNode()
        onNodeWithText("Undo").performClick()

        assertEquals("local-1", undoLocalId)
    }

    @Composable
    private fun screen(state: UserFeedUiState) {
        UserManagementTheme {
            UserFeedScreen(
                state = state,
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

    private fun user() = UserItemUiModel(
        localId = "local-1",
        name = "Ada Lovelace",
        email = "ada@example.com",
        gender = Gender.Female,
        status = UserStatus.Active,
        relativeTime = "2 minutes ago",
        synchronization = UserItemSynchronization.Synced,
    )
}
