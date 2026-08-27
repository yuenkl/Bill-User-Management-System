package com.bill.usermanagmentsystem.ui.users

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.bill.usermanagmentsystem.domain.model.AddUserInput
import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.UserStatus
import com.bill.usermanagmentsystem.ui.theme.UserManagementTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class UserFeedScreenTest {
    @Test
    fun shimmerIsShownOnlyForInitialLoading() =
        runComposeUiTest {
            setContent { screen(UserFeedUiState(initialLoading = true)) }

            assertTrue(onAllNodesWithContentDescription("Loading users").fetchSemanticsNodes().isNotEmpty())
            assertTrue(onAllNodesWithText("No users yet").fetchSemanticsNodes().isEmpty())
        }

    @Test
    fun userCardExposesPrimaryIdentityAndTimestamp() =
        runComposeUiTest {
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
    fun emptyOfflineAndErrorStatesRemainDistinct() =
        runComposeUiTest {
            var state by mutableStateOf(
                UserFeedUiState(
                    initialLoading = false,
                    emptyState = UserFeedEmptyState.Empty,
                ),
            )
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
    fun cachedOfflineFeedShowsBannerAndCard() =
        runComposeUiTest {
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
    fun scrollToTopEventAnimatesTheFeedToItsFirstUser() =
        runComposeUiTest {
            val events = MutableSharedFlow<UserFeedEvent>(extraBufferCapacity = 1)
            setContent {
                screen(
                    state = UserFeedUiState(users = users(30), initialLoading = false),
                    events = events,
                )
            }

            onNodeWithContentDescription("Users").performScrollToIndex(29)
            runOnIdle { events.tryEmit(UserFeedEvent.ScrollToTop) }

            onNodeWithText("User 1").assertIsDisplayed()
        }

    @Test
    fun accessibleFabForwardsAddUserEvent() =
        runComposeUiTest {
            var addCalls = 0
            setContent {
                UserManagementTheme {
                    UserFeedScreen(
                        state = UserFeedUiState(initialLoading = false),
                        onAddUser = { addCalls += 1 },
                        onAddUserDismissed = {},
                        onAddUserNameChanged = {},
                        onAddUserEmailChanged = {},
                        onAddUserGenderSelected = {},
                        onAddUserStatusSelected = {},
                        onAddUserSubmitted = {},
                    )
                }
            }

            onNodeWithContentDescription("Add user").performClick()
            assertEquals(1, addCalls)
        }

    @Test
    fun topBarDoesNotRenderRefreshButton() =
        runComposeUiTest {
            setContent {
                screen(state = UserFeedUiState(initialLoading = false))
            }

            assertTrue(onAllNodesWithText("Refresh").fetchSemanticsNodes().isEmpty())
        }

    @Test
    @Config(qualifiers = "w800dp-h1000dp")
    fun compactFormUsesSheetAndRendersValues() =
        runComposeUiTest {
            val events = MutableSharedFlow<UserFeedEvent>(extraBufferCapacity = 1)
            val form =
                AddUserFormUiState(
                    details = validAddUserDetails(),
                    isValid = true,
                )
            setContent {
                Box(Modifier.size(width = 500.dp, height = 800.dp)) {
                    screen(
                        state = UserFeedUiState(initialLoading = false, addUserForm = form),
                        events = events,
                    )
                }
            }
            runOnIdle { events.tryEmit(UserFeedEvent.ShowAddUserForm) }

            onNodeWithContentDescription("Add user sheet").fetchSemanticsNode()
            onNodeWithText("Ada Lovelace").fetchSemanticsNode()
            onNodeWithText("ada@example.com").fetchSemanticsNode()
        }

    @Test
    @Config(qualifiers = "w800dp-h1000dp")
    fun compactFormShowsSubmitAction() =
        runComposeUiTest {
            var submissions = 0
            val events = MutableSharedFlow<UserFeedEvent>(extraBufferCapacity = 1)
            val form =
                AddUserFormUiState(
                    details = validAddUserDetails(),
                    isValid = true,
                )
            setContent {
                Box(Modifier.size(width = 500.dp, height = 800.dp)) {
                    screen(
                        state = UserFeedUiState(initialLoading = false, addUserForm = form),
                        events = events,
                        onAddUserSubmitted = { submissions += 1 },
                    )
                }
            }
            runOnIdle { events.tryEmit(UserFeedEvent.ShowAddUserForm) }

            onNodeWithContentDescription("Submit user").performClick()
            assertEquals(1, submissions)
        }

    @Test
    fun apiValidationAlertEventShowsEveryFieldMessageAndCanBeDismissed() =
        runComposeUiTest {
            val events = MutableSharedFlow<UserFeedEvent>(extraBufferCapacity = 1)
            setContent {
                screen(
                    state = UserFeedUiState(initialLoading = false),
                    events = events,
                )
            }
            runOnIdle {
                events.tryEmit(
                    UserFeedEvent.ShowAddUserValidationAlert(
                        AddUserValidationAlert(
                            errors =
                                listOf(
                                    AddUserApiFieldError("email", "has already been taken"),
                                    AddUserApiFieldError("gender", "is invalid"),
                                ),
                        ),
                    ),
                )
            }

            onNodeWithText("Unable to add user").fetchSemanticsNode()
            onNodeWithText("Email").fetchSemanticsNode()
            onNodeWithText("has already been taken").fetchSemanticsNode()
            onNodeWithText("Gender").fetchSemanticsNode()
            onNodeWithText("is invalid").fetchSemanticsNode()
            onNodeWithText("OK").performClick()
            assertTrue(onAllNodesWithText("Unable to add user").fetchSemanticsNodes().isEmpty())
        }

    @Test
    fun adaptiveFormPresentationUsesAvailableWindowOrientation() {
        assertEquals(AdaptiveLayoutMode.Compact, adaptiveLayoutMode(width = 900.dp, height = 1_200.dp))
        assertEquals(AdaptiveLayoutMode.Wide, adaptiveLayoutMode(width = 700.dp, height = 500.dp))
    }

    @Test
    @Config(qualifiers = "w800dp-h1000dp")
    fun portraitFeedUsesOneColumnRegardlessOfItsWidth() =
        runComposeUiTest {
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

            val centers =
                threeUsers().map { item ->
                    onNodeWithContentDescription(item.accessibilityLabel()).fetchSemanticsNode().boundsInRoot.center
                }
            assertTrue(centers.all { center -> abs(center.x - centers.first().x) < 1f })
            assertTrue(centers[1].y > centers[0].y)
            assertTrue(centers[2].y > centers[1].y)
        }

    @Test
    @Config(qualifiers = "w800dp-h1000dp")
    fun landscapeFeedUsesExactlyTwoColumnsRegardlessOfItsWidth() =
        runComposeUiTest {
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

            val centers =
                threeUsers().map { item ->
                    onNodeWithContentDescription(item.accessibilityLabel()).fetchSemanticsNode().boundsInRoot.center
                }
            assertTrue(abs(centers[0].y - centers[1].y) < 1f)
            assertTrue(centers[1].x > centers[0].x)
            assertTrue(abs(centers[0].x - centers[2].x) < 1f)
            assertTrue(centers[2].y > centers[0].y)
        }

    @Test
    @Config(qualifiers = "w800dp-h1000dp")
    fun formExposesValidationErrorAndDisabledSubmitSemantics() =
        runComposeUiTest {
            val events = MutableSharedFlow<UserFeedEvent>(extraBufferCapacity = 1)
            setContent {
                Box(Modifier.size(width = 500.dp, height = 800.dp)) {
                    screen(
                        UserFeedUiState(
                            initialLoading = false,
                            addUserForm =
                                AddUserFormUiState(
                                    details =
                                        listOf(
                                            AddUserFormEntry(
                                                type = AddUserFormEntryType.Name,
                                                value = "A",
                                                error = "Name must be at least 2 characters.",
                                            ),
                                        ),
                                ),
                        ),
                        events = events,
                    )
                }
            }
            runOnIdle { events.tryEmit(UserFeedEvent.ShowAddUserForm) }

            onNode(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.Error,
                    "Name must be at least 2 characters.",
                ),
            ).fetchSemanticsNode()
            onNodeWithContentDescription("Submit user").assertIsNotEnabled()
        }

    private fun validAddUserDetails(): List<AddUserFormEntry> =
        listOf(
            AddUserFormEntry(AddUserFormEntryType.Name, value = "Ada Lovelace"),
            AddUserFormEntry(AddUserFormEntryType.Email, value = "ada@example.com"),
            AddUserFormEntry(AddUserFormEntryType.Gender, value = Gender.Female.apiValue),
            AddUserFormEntry(AddUserFormEntryType.Status, value = UserStatus.Active.apiValue),
        )

    @Test
    @Config(qualifiers = "w500dp-h800dp")
    fun reachingTheEndOfTheFeedRequestsMoreUsersOnce() =
        runComposeUiTest {
            var pageCalls = 0
            val users = users(20)
            setContent {
                screen(
                    state =
                        UserFeedUiState(
                            users = users,
                            initialLoading = false,
                            canLoadMore = true,
                        ),
                    onLoadMore = { pageCalls += 1 },
                )
            }

            onNodeWithContentDescription("Users").performScrollToIndex(users.size)
            waitForIdle()

            assertEquals(1, pageCalls)
        }

    @Test
    fun emptyReportedPageContinuesWithThePrecedingPage() =
        runComposeUiTest {
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
                    onLoadMore = {
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
    fun pageFailureShowsAnExplicitRetryAction() =
        runComposeUiTest {
            var retryCalls = 0
            setContent {
                screen(
                    state =
                        UserFeedUiState(
                            users = listOf(user()),
                            initialLoading = false,
                            canLoadMore = true,
                            loadMoreError = "You're offline.",
                        ),
                    onRetryLoadMore = { retryCalls += 1 },
                )
            }

            onNodeWithText("Couldn't load more users").fetchSemanticsNode()
            onNodeWithText("Retry").performClick()

            assertEquals(1, retryCalls)
        }

    @Test
    fun longClickOpensIdentityConfirmationAndCancelDoesNotDelete() =
        runComposeUiTest {
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
                        onAddUser = {},
                        onAddUserDismissed = {},
                        onAddUserNameChanged = {},
                        onAddUserEmailChanged = {},
                        onAddUserGenderSelected = {},
                        onAddUserStatusSelected = {},
                        onAddUserSubmitted = {},
                        onUserLongClick = { localId ->
                            state =
                                state.copy(
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
    fun undoSnackbarNamesUserAndForwardsUndoAction() =
        runComposeUiTest {
            var restoredInput: AddUserInput? = null
            val events = MutableSharedFlow<UserFeedEvent>(extraBufferCapacity = 1)
            val input =
                AddUserInput(
                    name = "Ada Lovelace",
                    email = "ada@example.com",
                    gender = Gender.Female,
                    status = UserStatus.Active,
                )
            setContent {
                UserManagementTheme {
                    UserFeedScreen(
                        state =
                            UserFeedUiState(
                                initialLoading = false,
                                emptyState = UserFeedEmptyState.Empty,
                            ),
                        events = events,
                        onAddUser = {},
                        onAddUserDismissed = {},
                        onAddUserNameChanged = {},
                        onAddUserEmailChanged = {},
                        onAddUserGenderSelected = {},
                        onAddUserStatusSelected = {},
                        onAddUserSubmitted = {},
                        onUndoDelete = { restoredInput = it },
                    )
                }
            }
            runOnIdle {
                events.tryEmit(UserFeedEvent.ShowDeleteUndoSnackbar("Ada Lovelace", input))
            }

            onNodeWithText("Ada Lovelace deleted").fetchSemanticsNode()
            onNodeWithText("Undo").performClick()

            assertEquals("ada@example.com", restoredInput?.email)
        }

    @Test
    fun undoSnackbarProvidesAnExplicitDismissAction() =
        runComposeUiTest {
            var dismissedInput: AddUserInput? = null
            val input =
                AddUserInput(
                    name = "Ada Lovelace",
                    email = "ada@example.com",
                    gender = Gender.Female,
                    status = UserStatus.Active,
                )
            val events = MutableSharedFlow<UserFeedEvent>(extraBufferCapacity = 1)
            setContent {
                screen(
                    state = UserFeedUiState(initialLoading = false),
                    events = events,
                    onUndoDeleteDismissed = { dismissedInput = it },
                )
            }
            runOnIdle {
                events.tryEmit(UserFeedEvent.ShowDeleteUndoSnackbar("Ada Lovelace", input))
            }

            onNodeWithText("Dismiss").performClick()

            assertEquals(input, dismissedInput)
        }

    @Composable
    private fun screen(
        state: UserFeedUiState,
        events: Flow<UserFeedEvent> = emptyFlow(),
        onLoadMore: () -> Unit = {},
        onRetryLoadMore: () -> Unit = {},
        onAddUserSubmitted: () -> Unit = {},
        onUndoDeleteDismissed: (AddUserInput) -> Unit = {},
    ) {
        UserManagementTheme {
            UserFeedScreen(
                state = state,
                events = events,
                onAddUser = {},
                onAddUserDismissed = {},
                onAddUserNameChanged = {},
                onAddUserEmailChanged = {},
                onAddUserGenderSelected = {},
                onAddUserStatusSelected = {},
                onAddUserSubmitted = onAddUserSubmitted,
                onUndoDeleteDismissed = onUndoDeleteDismissed,
                onLoadMore = onLoadMore,
                onRetryLoadMore = onRetryLoadMore,
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
    )

    private fun threeUsers(): List<UserItemUiModel> =
        listOf(
            user(),
            user(localId = "local-2", name = "Grace Hopper", email = "grace@example.com"),
            user(localId = "local-3", name = "Katherine Johnson", email = "katherine@example.com"),
        )

    private fun users(count: Int): List<UserItemUiModel> =
        (1..count).map { index ->
            user(
                localId = "local-$index",
                name = "User $index",
                email = "user$index@example.com",
            )
        }

    private fun UserItemUiModel.accessibilityLabel(): String = "$name, $email, $relativeTime"
}
