package com.bill.usermanagmentsystem.ui.users

import androidx.lifecycle.viewModelScope
import com.bill.usermanagmentsystem.domain.model.AddUserInput
import com.bill.usermanagmentsystem.domain.model.DeletedUserUndo
import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.User
import com.bill.usermanagmentsystem.domain.model.UserDataError
import com.bill.usermanagmentsystem.domain.model.UserDataException
import com.bill.usermanagmentsystem.domain.model.UserRecord
import com.bill.usermanagmentsystem.domain.model.UserStatus
import com.bill.usermanagmentsystem.domain.repository.PageLoadResult
import com.bill.usermanagmentsystem.domain.usecase.AddUser
import com.bill.usermanagmentsystem.domain.usecase.AddUserValidator
import com.bill.usermanagmentsystem.domain.usecase.DeleteUser
import com.bill.usermanagmentsystem.domain.usecase.LoadNextUsersPage
import com.bill.usermanagmentsystem.domain.usecase.ObserveUsers
import com.bill.usermanagmentsystem.domain.usecase.RefreshUsers
import com.bill.usermanagmentsystem.domain.usecase.RelativeTimeFormatter
import com.bill.usermanagmentsystem.domain.usecase.UndoUserDeletion
import com.bill.usermanagmentsystem.platform.AppLifecycleObserver
import com.bill.usermanagmentsystem.platform.AppLifecycleState
import com.bill.usermanagmentsystem.platform.ConnectivityObserver
import com.bill.usermanagmentsystem.platform.ConnectivityStatus
import com.bill.usermanagmentsystem.platform.TimeProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class UserFeedViewModelTest {
    @Test
    fun initialRefreshLoadsAndMakesPaginationAvailable() =
        runTest {
            withFixture {
                runCurrent()

                assertEquals(1, refreshCalls)
                assertTrue(!viewModel.uiState.value.initialLoading)
                assertTrue(viewModel.uiState.value.canLoadMore)
            }
        }

    @Test
    fun refreshFailureKeepsCachedUsersAndShowsAMessage() =
        runTest {
            withFixture {
                users.value = listOf(userRecord())
                refreshHandler = {
                    Result.failure(UserDataException(UserDataError.RemoteContract("Unavailable")))
                }
                runCurrent()

                val event = async { viewModel.events.first() }
                runCurrent()
                viewModel.refresh()
                runCurrent()

                assertEquals(
                    "Ada Lovelace",
                    viewModel.uiState.value.users
                        .single()
                        .name,
                )
                assertNotNull(viewModel.uiState.value.banner)
                assertEquals(
                    UserFeedEvent.ShowSnackbar(
                        message = "The service returned unexpected data. Unavailable",
                    ),
                    event.await(),
                )
            }
        }

    @Test
    fun nextPageRequestsAreDeduplicatedAndExposeRemainingAvailability() =
        runTest {
            val gate = CompletableDeferred<Result<PageLoadResult>>()
            withFixture {
                pageHandler = { gate.await() }
                runCurrent()

                viewModel.loadNextPage()
                viewModel.loadNextPage()
                runCurrent()

                assertEquals(1, pageCalls)
                assertTrue(viewModel.uiState.value.loadingMore)

                gate.complete(Result.success(PageLoadResult(loadedCount = 20, hasMore = false)))
                runCurrent()

                assertTrue(!viewModel.uiState.value.loadingMore)
                assertTrue(!viewModel.uiState.value.canLoadMore)
            }
        }

    @Test
    fun nextPageFailureRequiresExplicitRetry() =
        runTest {
            withFixture {
                pageHandler = { Result.failure(UserDataException(UserDataError.Offline)) }
                runCurrent()

                viewModel.loadNextPage()
                runCurrent()
                assertNotNull(viewModel.uiState.value.loadMoreError)

                viewModel.loadNextPage()
                runCurrent()
                assertEquals(1, pageCalls)

                pageHandler = { Result.success(PageLoadResult(loadedCount = 5, hasMore = false)) }
                viewModel.retryNextPage()
                runCurrent()
                assertEquals(2, pageCalls)
            }
        }

    @Test
    fun automaticConnectivityAndForegroundTriggersRefresh() =
        runTest {
            withFixture(connectivityStatus = ConnectivityStatus.Unavailable) {
                runCurrent()
                assertEquals(1, refreshCalls)

                connectivity.status.value = ConnectivityStatus.Available
                runCurrent()
                lifecycle.mutableState.value = AppLifecycleState.Foreground
                runCurrent()

                assertEquals(3, refreshCalls)
            }
        }

    @Test
    fun minuteTickRefreshesRelativeLabelsWithoutChangingTheDatabase() =
        runTest {
            withFixture {
                users.value = listOf(userRecord(observedAt = clock.current))
                runCurrent()
                assertEquals(
                    "Just now",
                    viewModel.uiState.value.users
                        .single()
                        .relativeTime,
                )

                clock.current += 2.minutes
                advanceTimeBy(1.minutes.inWholeMilliseconds)
                runCurrent()

                assertEquals(
                    "2 minutes ago",
                    viewModel.uiState.value.users
                        .single()
                        .relativeTime,
                )
                assertEquals(1, users.value.size)
            }
        }

    @Test
    fun invalidFormShowsErrorsThenSubmitsNormalizedInput() =
        runTest {
            withFixture {
                viewModel.openAddUserForm()
                viewModel.updateAddUserName(" ")
                runCurrent()
                assertEquals(
                    "Enter a name.",
                    viewModel.uiState.value.addUserForm
                        ?.errorMessage(AddUserFormEntryType.Name),
                )

                viewModel.updateAddUserName("  Ada Lovelace  ")
                viewModel.updateAddUserEmail(" ada@example.com ")
                viewModel.selectAddUserGender(Gender.Female)
                val event = async { viewModel.events.first() }
                runCurrent()
                viewModel.submitAddUser()
                runCurrent()

                assertEquals("Ada Lovelace", addInputs.single().name)
                assertEquals("ada@example.com", addInputs.single().email)
                assertNull(viewModel.uiState.value.addUserForm)
                assertEquals(UserFeedEvent.ScrollToTop, event.await())
            }
        }

    @Test
    fun validationFailureShowsTheApiFieldErrorAndKeepsTheFormOpen() =
        runTest {
            withFixture {
                addHandler = {
                    Result.failure(
                        UserDataException(UserDataError.ValidationRejected("email: has already been taken")),
                    )
                }
                viewModel.openAddUserForm()
                viewModel.updateAddUserName("Ada Lovelace")
                viewModel.updateAddUserEmail("ada@example.com")
                viewModel.selectAddUserGender(Gender.Female)
                viewModel.submitAddUser()
                runCurrent()

                assertEquals(
                    "has already been taken",
                    viewModel.uiState.value.addUserForm
                        ?.errorMessage(AddUserFormEntryType.Email),
                )
                assertEquals(
                    AddUserApiFieldError("email", "has already been taken"),
                    viewModel.uiState.value.addUserValidationAlert
                        ?.errors
                        ?.single(),
                )
            }
        }

    @Test
    fun confirmedDeleteShowsUndoAndUndoRestoresOnce() =
        runTest {
            withFixture {
                users.value = listOf(userRecord())
                runCurrent()

                val event = async { viewModel.events.first() }
                runCurrent()
                viewModel.selectUserForDeletion("local-1")
                viewModel.confirmDelete()
                viewModel.confirmDelete()
                runCurrent()

                assertEquals(listOf("local-1"), deleteRequests)
                val input =
                    (event.await() as UserFeedEvent.ShowDeleteUndoSnackbar)
                        .input
                val scrollEvent = async { viewModel.events.first() }
                runCurrent()
                viewModel.undoDelete(input)
                viewModel.undoDelete(input)
                runCurrent()

                assertEquals(listOf(input), undoRequests)
                assertEquals(UserFeedEvent.ScrollToTop, scrollEvent.await())
            }
        }

    @Test
    fun dismissedUndoCannotRestoreDeletedUser() =
        runTest {
            withFixture {
                users.value = listOf(userRecord())
                runCurrent()

                val event = async { viewModel.events.first() }
                runCurrent()
                viewModel.selectUserForDeletion("local-1")
                viewModel.confirmDelete()
                runCurrent()

                val input = (event.await() as UserFeedEvent.ShowDeleteUndoSnackbar).input
                viewModel.dismissUndoDelete(input)
                viewModel.undoDelete(input)
                runCurrent()

                assertTrue(undoRequests.isEmpty())
            }
        }

    private suspend fun kotlinx.coroutines.test.TestScope.withFixture(
        connectivityStatus: ConnectivityStatus = ConnectivityStatus.Available,
        block: suspend Fixture.() -> Unit,
    ) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val fixture = Fixture(dispatcher, connectivityStatus)
        try {
            fixture.block()
        } finally {
            fixture.viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private class Fixture(
        dispatcher: CoroutineDispatcher,
        connectivityStatus: ConnectivityStatus,
    ) {
        val users = MutableStateFlow<List<UserRecord>>(emptyList())
        val connectivity = FakeConnectivityObserver(connectivityStatus)
        val lifecycle = FakeLifecycleObserver()
        val clock = FakeTimeProvider(Instant.parse("2026-08-26T12:00:00Z"))
        var refreshCalls = 0
        var refreshHandler: suspend () -> Result<Unit> = { Result.success(Unit) }
        var pageCalls = 0
        var pageHandler: suspend () -> Result<PageLoadResult> = {
            Result.success(PageLoadResult(loadedCount = 0, hasMore = true))
        }
        val addInputs = mutableListOf<AddUserInput>()
        var addHandler: suspend (AddUserInput) -> Result<String> = { Result.success("created") }
        val deleteRequests = mutableListOf<String>()
        var deleteHandler: suspend (String) -> Result<DeletedUserUndo> = {
            Result.success(deletedUserUndo())
        }
        val undoRequests = mutableListOf<AddUserInput>()
        var undoHandler: suspend (AddUserInput) -> Result<String> = { Result.success("restored") }
        val viewModel =
            UserFeedViewModel(
                observeUsers = ObserveUsers { users },
                refreshUsers =
                    RefreshUsers {
                        refreshCalls += 1
                        refreshHandler()
                    },
                loadNextUsersPage =
                    LoadNextUsersPage {
                        pageCalls += 1
                        pageHandler()
                    },
                addUser =
                    AddUser { input ->
                        addInputs += input
                        addHandler(input)
                    },
                addUserValidator = AddUserValidator(),
                deleteUser =
                    DeleteUser { localId ->
                        deleteRequests += localId
                        deleteHandler(localId)
                    },
                undoUserDeletion =
                    UndoUserDeletion { input ->
                        undoRequests += input
                        undoHandler(input)
                    },
                connectivityObserver = connectivity,
                lifecycleObserver = lifecycle,
                timeProvider = clock,
                relativeTimeFormatter = RelativeTimeFormatter(),
                dispatcher = dispatcher,
            )

        private fun deletedUserUndo() =
            DeletedUserUndo(
                userName = "Ada Lovelace",
                input =
                    AddUserInput(
                        name = "Ada Lovelace",
                        email = "ada@example.com",
                        gender = Gender.Female,
                        status = UserStatus.Active,
                    ),
            )
    }

    private class FakeConnectivityObserver(
        initial: ConnectivityStatus,
    ) : ConnectivityObserver {
        override val status = MutableStateFlow(initial)
    }

    private class FakeLifecycleObserver : AppLifecycleObserver {
        val mutableState = MutableStateFlow(AppLifecycleState.Background)
        override val state: StateFlow<AppLifecycleState> = mutableState
    }

    private class FakeTimeProvider(
        var current: Instant,
    ) : TimeProvider {
        override fun now(): Instant = current
    }

    private fun userRecord(observedAt: Instant = Instant.parse("2026-08-26T12:00:00Z")) =
        UserRecord(
            user =
                User(
                    localId = "local-1",
                    remoteId = 42,
                    name = "Ada Lovelace",
                    email = "ada@example.com",
                    gender = Gender.Female,
                    status = UserStatus.Active,
                    observedAt = observedAt,
                ),
        )
}
