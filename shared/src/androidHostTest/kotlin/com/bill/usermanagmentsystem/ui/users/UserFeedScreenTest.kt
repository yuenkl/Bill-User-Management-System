package com.bill.usermanagmentsystem.ui.users

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.bill.usermanagmentsystem.domain.model.AddUserInput
import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.UserStatus
import com.bill.usermanagmentsystem.ui.theme.UserManagementTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.abs
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
            details = validAddUserDetails(),
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
    @Config(qualifiers = "w800dp-h1000dp")
    fun compactFormShowsSubmitAction() = runComposeUiTest {
        var submissions = 0
        val form = AddUserFormUiState(
            details = validAddUserDetails(),
            isValid = true,
        )
        setContent {
            Box(Modifier.size(width = 500.dp, height = 800.dp)) {
                screen(
                    state = UserFeedUiState(initialLoading = false, addUserForm = form),
                    onAddUserSubmitted = { submissions += 1 },
                )
            }
        }

        onNodeWithContentDescription("Submit user").performClick()
        assertEquals(1, submissions)
    }

    @Test
    fun adaptiveFormPresentationUsesAvailableWindowOrientation() {
        assertEquals(AdaptiveLayoutMode.Compact, adaptiveLayoutMode(width = 900.dp, height = 1_200.dp))
        assertEquals(AdaptiveLayoutMode.Wide, adaptiveLayoutMode(width = 700.dp, height = 500.dp))
    }

    @Test
    @Config(qualifiers = "w800dp-h1000dp")
    fun portraitFeedUsesOneColumnRegardlessOfItsWidth() = runComposeUiTest {
        setContent {
            Box(Modifier.size(width = 700.dp, height = 800.dp)) {
                screen(
                    UserFeedUiState(
                        users = threeUsers(),
                        initialLoading = false,
                    ),
                )
            }
        }

        val centers = threeUsers().map { item ->
            onNodeWithContentDescription(item.accessibilityLabel()).fetchSemanticsNode().boundsInRoot.center
        }
        assertTrue(centers.all { center -> abs(center.x - centers.first().x) < 1f })
        assertTrue(centers[1].y > centers[0].y)
        assertTrue(centers[2].y > centers[1].y)
    }

    @Test
    @Config(qualifiers = "w800dp-h1000dp")
    fun landscapeFeedUsesExactlyTwoColumnsRegardlessOfItsWidth() = runComposeUiTest {
        setContent {
            Box(Modifier.size(width = 500.dp, height = 400.dp)) {
                screen(
                    UserFeedUiState(
                        users = threeUsers(),
                        initialLoading = false,
                    ),
                )
            }
        }

        val centers = threeUsers().map { item ->
            onNodeWithContentDescription(item.accessibilityLabel()).fetchSemanticsNode().boundsInRoot.center
        }
        assertTrue(abs(centers[0].y - centers[1].y) < 1f)
        assertTrue(centers[1].x > centers[0].x)
        assertTrue(abs(centers[0].x - centers[2].x) < 1f)
        assertTrue(centers[2].y > centers[0].y)
    }

    @Test
    @Config(qualifiers = "w800dp-h1000dp")
    fun formExposesValidationErrorAndDisabledSubmitSemantics() = runComposeUiTest {
        setContent {
            Box(Modifier.size(width = 500.dp, height = 800.dp)) {
                screen(
                    UserFeedUiState(
                        initialLoading = false,
                        addUserForm = AddUserFormUiState(
                            details = listOf(
                                UserDetail(
                                    type = AddUserField.Name,
                                    value = "A",
                                    error = "Name must be at least 2 characters.",
                                ),
                            ),
                        ),
                    ),
                )
            }
        }

        onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.Error,
                "Name must be at least 2 characters.",
            ),
        ).fetchSemanticsNode()
        onNodeWithContentDescription("Submit user").assertIsNotEnabled()
    }

    private fun validAddUserDetails(): List<UserDetail> = listOf(
        UserDetail(AddUserField.Name, value = "Ada Lovelace"),
        UserDetail(AddUserField.Email, value = "ada@example.com"),
        UserDetail(AddUserField.Gender, value = Gender.Female.apiValue),
        UserDetail(AddUserField.Status, value = UserStatus.Active.apiValue),
    )

    @Test
    fun pendingAndFailedSyncStatesHaveReadableText() = runComposeUiTest {
        setContent {
            screen(
                UserFeedUiState(
                    users = listOf(
                        user().copy(synchronization = UserItemSynchronization.Pending),
                        user(localId = "local-2", name = "Grace Hopper").copy(
                            synchronization = UserItemSynchronization.Failed("email: already exists"),
                        ),
                    ),
                    initialLoading = false,
                ),
            )
        }

        onNodeWithText("Pending sync").fetchSemanticsNode()
        onNodeWithText("Sync failed: email: already exists").fetchSemanticsNode()
        onNodeWithText("Retry sync").fetchSemanticsNode()
    }

    @Test
    @Config(qualifiers = "w500dp-h800dp")
    fun reachingTheEndOfTheFeedRequestsTheNextPageOnce() = runComposeUiTest {
        var pageCalls = 0
        val users = users(20)
        setContent {
            screen(
                state = UserFeedUiState(
                    users = users,
                    initialLoading = false,
                    canLoadMore = true,
                ),
                onLoadNextPage = { pageCalls += 1 },
            )
        }

        onNodeWithContentDescription("Users").performScrollToIndex(users.size)
        waitForIdle()

        assertEquals(1, pageCalls)
    }

    @Test
    fun emptyReportedLastPageContinuesWithThePrecedingPage() = runComposeUiTest {
        var pageCalls = 0
        var state by mutableStateOf(
            UserFeedUiState(
                initialLoading = false,
                canLoadMore = true,
                emptyState = UserFeedEmptyState.Empty,
            ),
        )
        setContent {
            screen(
                state = state,
                onLoadNextPage = {
                    pageCalls += 1
                    if (pageCalls == 1) state = state.copy(loadingMore = true)
                },
            )
        }

        waitForIdle()
        state = state.copy(loadingMore = false)
        waitForIdle()

        assertEquals(2, pageCalls)
    }

    @Test
    fun pageFailureShowsAnExplicitRetryAction() = runComposeUiTest {
        var retryCalls = 0
        setContent {
            screen(
                state = UserFeedUiState(
                    users = listOf(user()),
                    initialLoading = false,
                    canLoadMore = true,
                    loadMoreError = "You're offline.",
                ),
                onRetryNextPage = { retryCalls += 1 },
            )
        }

        onNodeWithText("Couldn't load more users").fetchSemanticsNode()
        onNodeWithText("Retry").performClick()

        assertEquals(1, retryCalls)
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
        var restoredInput: AddUserInput? = null
        setContent {
            UserManagementTheme {
                UserFeedScreen(
                    state = UserFeedUiState(
                        initialLoading = false,
                        emptyState = UserFeedEmptyState.Empty,
                        undoSnackbar = DeleteUndoUiModel(
                            userName = "Ada Lovelace",
                            input = AddUserInput(
                                name = "Ada Lovelace",
                                email = "ada@example.com",
                                gender = Gender.Female,
                                status = UserStatus.Active,
                            ),
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
                    onUndoDelete = { restoredInput = it },
                )
            }
        }

        onNodeWithText("Ada Lovelace deleted").fetchSemanticsNode()
        onNodeWithText("Undo").performClick()

        assertEquals("ada@example.com", restoredInput?.email)
    }

    @Composable
    private fun screen(
        state: UserFeedUiState,
        onLoadNextPage: () -> Unit = {},
        onRetryNextPage: () -> Unit = {},
        onAddUserSubmitted: () -> Unit = {},
    ) {
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
                onAddUserSubmitted = onAddUserSubmitted,
                onRetryUserCreation = {},
                onMessageConsumed = {},
                onLoadNextPage = onLoadNextPage,
                onRetryNextPage = onRetryNextPage,
            )
        }
    }

    private fun user(
        localId: String = "local-1",
        name: String = "Ada Lovelace",
        email: String = "ada@example.com",
    ) = UserItemUiModel(
        localId = localId,
        name = name,
        email = email,
        gender = Gender.Female,
        status = UserStatus.Active,
        relativeTime = "2 minutes ago",
        synchronization = UserItemSynchronization.Synced,
    )

    private fun threeUsers(): List<UserItemUiModel> = listOf(
        user(),
        user(localId = "local-2", name = "Grace Hopper", email = "grace@example.com"),
        user(localId = "local-3", name = "Katherine Johnson", email = "katherine@example.com"),
    )

    private fun users(count: Int): List<UserItemUiModel> = (1..count).map { index ->
        user(
            localId = "local-$index",
            name = "User $index",
            email = "user$index@example.com",
        )
    }

    private fun UserItemUiModel.accessibilityLabel(): String =
        "$name, $email, $relativeTime"
}
