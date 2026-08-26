package com.bill.usermanagmentsystem.ui.users

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.UserStatus
import com.bill.usermanagmentsystem.ui.theme.UserManagementTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
                    onMessageConsumed = {},
                )
            }
        }

        onNodeWithText("Retry").performClick()
        onNodeWithText("Refresh").performClick()

        assertEquals(1, retryCalls)
        assertEquals(1, refreshCalls)
    }

    @Composable
    private fun screen(state: UserFeedUiState) {
        UserManagementTheme {
            UserFeedScreen(
                state = state,
                onRefresh = {},
                onRetry = {},
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
