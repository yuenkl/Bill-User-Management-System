package com.bill.usermanagmentsystem.ui.users

import androidx.lifecycle.viewModelScope
import com.bill.usermanagmentsystem.domain.model.AddUserInput
import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.SyncState
import com.bill.usermanagmentsystem.domain.model.UndoableDeletion
import com.bill.usermanagmentsystem.domain.model.User
import com.bill.usermanagmentsystem.domain.model.UserDataError
import com.bill.usermanagmentsystem.domain.model.UserDataException
import com.bill.usermanagmentsystem.domain.model.UserRecord
import com.bill.usermanagmentsystem.domain.model.UserStatus
import com.bill.usermanagmentsystem.domain.model.UserSynchronization
import com.bill.usermanagmentsystem.domain.usecase.AddUser
import com.bill.usermanagmentsystem.domain.usecase.AddUserValidator
import com.bill.usermanagmentsystem.domain.usecase.DeleteUserWithUndo
import com.bill.usermanagmentsystem.domain.usecase.FinalizeExpiredDeletions
import com.bill.usermanagmentsystem.domain.usecase.ObserveSyncState
import com.bill.usermanagmentsystem.domain.usecase.ObserveUndoableDeletions
import com.bill.usermanagmentsystem.domain.usecase.ObserveUsers
import com.bill.usermanagmentsystem.domain.usecase.RefreshUsers
import com.bill.usermanagmentsystem.domain.usecase.RelativeTimeFormatter
import com.bill.usermanagmentsystem.domain.usecase.RetryUserCreation
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class UserFeedViewModelTest {
    @Test
    fun coldStartSynchronizesOnceAndPublishesDatabaseUsers() = runTest {
        withFixture {
            users.value = listOf(userRecord())
            runCurrent()

            assertEquals(1, refreshCalls)
            assertEquals("Ada Lovelace", viewModel.uiState.value.users.single().name)
            assertNull(viewModel.uiState.value.emptyState)
        }
    }

    @Test
    fun failedCachedRefreshKeepsUsersAndExposesConsumableMessage() = runTest {
        withFixture {
            users.value = listOf(userRecord())
            runCurrent()
            refreshHandler = {
                syncState.value = SyncState.Failed(UserDataError.RemoteContract("bad payload"))
                Result.failure(UserDataException(UserDataError.RemoteContract("bad payload")))
            }

            viewModel.refresh()
            runCurrent()

            assertEquals(1, viewModel.uiState.value.users.size)
            assertIs<UserFeedBanner.RefreshFailed>(viewModel.uiState.value.banner)
            val message = assertNotNull(viewModel.uiState.value.message)
            viewModel.consumeMessage(message.id)
            runCurrent()
            assertNull(viewModel.uiState.value.message)
        }
    }

    @Test
    fun noCacheOfflineShowsDedicatedOfflineState() = runTest {
        withFixture(connectivityStatus = ConnectivityStatus.Unavailable) {
            syncState.value = SyncState.Failed(UserDataError.Offline)
            runCurrent()

            assertEquals(UserFeedEmptyState.Offline, viewModel.uiState.value.emptyState)
            assertTrue(viewModel.uiState.value.users.isEmpty())
        }
    }

    @Test
    fun cachedOfflineShowsNonBlockingBanner() = runTest {
        withFixture(connectivityStatus = ConnectivityStatus.Unavailable) {
            users.value = listOf(userRecord())
            syncState.value = SyncState.Failed(UserDataError.Offline)
            runCurrent()

            assertEquals(UserFeedBanner.Offline, viewModel.uiState.value.banner)
            assertEquals(1, viewModel.uiState.value.users.size)
        }
    }

    @Test
    fun authenticationFailureHasDedicatedEmptyStateAndRetryRunsAgain() = runTest {
        withFixture {
            syncState.value = SyncState.Failed(UserDataError.AuthenticationRequired)
            runCurrent()
            assertEquals(UserFeedEmptyState.AuthenticationRequired, viewModel.uiState.value.emptyState)

            viewModel.retry()
            runCurrent()
            assertEquals(2, refreshCalls)
        }
    }

    @Test
    fun overlappingRefreshesAreDeduplicated() = runTest {
        val gate = CompletableDeferred<Result<Unit>>()
        withFixture(initialRefreshHandler = { gate.await() }) {
            runCurrent()
            viewModel.refresh()
            viewModel.refresh()
            runCurrent()

            assertEquals(1, refreshCalls)
            gate.complete(Result.success(Unit))
            runCurrent()
        }
    }

    @Test
    fun startupConnectivityForegroundAndManualTriggersShareOneActiveSynchronization() = runTest {
        val gate = CompletableDeferred<Result<Unit>>()
        withFixture(initialRefreshHandler = { gate.await() }) {
            runCurrent()

            connectivity.status.value = ConnectivityStatus.Unavailable
            runCurrent()
            connectivity.status.value = ConnectivityStatus.Available
            lifecycle.mutableState.value = AppLifecycleState.Foreground
            viewModel.refresh()
            runCurrent()

            assertEquals(1, refreshCalls)
            gate.complete(Result.success(Unit))
            runCurrent()
        }
    }

    @Test
    fun connectivityAndForegroundTriggersSynchronizeWhenTheCoordinatorIsIdle() = runTest {
        withFixture(connectivityStatus = ConnectivityStatus.Unavailable) {
            runCurrent()
            assertEquals(1, refreshCalls)

            connectivity.status.value = ConnectivityStatus.Available
            runCurrent()
            assertEquals(2, refreshCalls)

            lifecycle.mutableState.value = AppLifecycleState.Foreground
            runCurrent()
            assertEquals(3, refreshCalls)
        }
    }

    @Test
    fun minuteTickRefreshesRelativeLabelsWithoutChangingDatabase() = runTest {
        withFixture {
            users.value = listOf(userRecord(observedAt = clock.current))
            runCurrent()
            assertEquals("Just now", viewModel.uiState.value.users.single().relativeTime)

            clock.current += 2.minutes
            advanceTimeBy(1.minutes.inWholeMilliseconds)
            runCurrent()

            assertEquals("2 minutes ago", viewModel.uiState.value.users.single().relativeTime)
            assertEquals(1, users.value.size)
        }
    }

    @Test
    fun formErrorsAppearAfterInteractionAndClearWhenCorrected() = runTest {
        withFixture {
            viewModel.openAddUserForm()
            runCurrent()
            assertNull(viewModel.uiState.value.addUserForm?.nameError)

            viewModel.updateAddUserName(" ")
            runCurrent()
            assertEquals("Enter a name.", viewModel.uiState.value.addUserForm?.nameError)

            viewModel.updateAddUserName("Ada Lovelace")
            runCurrent()
            assertNull(viewModel.uiState.value.addUserForm?.nameError)
        }
    }

    @Test
    fun submitRequiresEveryFieldNormalizesInputAndRejectsDuplicateTaps() = runTest {
        val gate = CompletableDeferred<Result<String>>()
        withFixture {
            addHandler = { gate.await() }
            viewModel.openAddUserForm()
            viewModel.updateAddUserName("  Ada Lovelace  ")
            viewModel.updateAddUserEmail(" ada@example.com ")
            runCurrent()
            assertEquals(false, viewModel.uiState.value.addUserForm?.canSubmit)

            viewModel.selectAddUserGender(Gender.Female)
            runCurrent()
            assertTrue(viewModel.uiState.value.addUserForm?.canSubmit == true)

            viewModel.submitAddUser()
            viewModel.submitAddUser()
            runCurrent()

            assertEquals(1, addInputs.size)
            assertEquals("Ada Lovelace", addInputs.single().name)
            assertEquals("ada@example.com", addInputs.single().email)
            assertTrue(viewModel.uiState.value.addUserForm?.submitting == true)

            gate.complete(Result.success("local-created"))
            runCurrent()
            assertNull(viewModel.uiState.value.addUserForm)
        }
    }

    @Test
    fun serverFieldFailureStaysSeparateUntilFieldChanges() = runTest {
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

            val failed = assertNotNull(viewModel.uiState.value.addUserForm)
            assertNull(failed.emailError)
            assertEquals("has already been taken", failed.emailApiError)
            assertEquals(false, failed.canSubmit)

            viewModel.updateAddUserEmail("ada2@example.com")
            runCurrent()
            assertNull(viewModel.uiState.value.addUserForm?.emailApiError)
            assertTrue(viewModel.uiState.value.addUserForm?.canSubmit == true)
        }
    }

    @Test
    fun retryCreateRejectsOverlappingTaps() = runTest {
        val gate = CompletableDeferred<Result<Unit>>()
        withFixture {
            retryHandler = { gate.await() }
            users.value = listOf(
                userRecord().copy(
                    synchronization = UserSynchronization.CreateFailed("email: already exists"),
                ),
            )
            runCurrent()

            viewModel.retryUserCreation("local-1")
            viewModel.retryUserCreation("local-1")
            runCurrent()
            assertEquals(listOf("local-1"), retryIds)
            assertTrue(
                (viewModel.uiState.value.users.single().synchronization as UserItemSynchronization.Failed)
                    .retrying,
            )

            gate.complete(Result.success(Unit))
            runCurrent()
        }
    }

    @Test
    fun selectionResolvesLatestUserAndCancelDoesNotDelete() = runTest {
        withFixture {
            users.value = listOf(
                userRecord(),
                userRecord(localId = "local-2", name = "Grace Hopper"),
            )
            runCurrent()

            viewModel.selectUserForDeletion("local-2")
            runCurrent()
            assertEquals("Grace Hopper", viewModel.uiState.value.deleteConfirmation?.name)

            users.value = listOf(userRecord())
            runCurrent()
            assertNull(viewModel.uiState.value.deleteConfirmation)

            viewModel.cancelDelete()
            runCurrent()
            assertTrue(deleteRequests.isEmpty())
        }
    }

    @Test
    fun confirmUsesSelectedLocalIdAndDeduplicatesRepeatedTaps() = runTest {
        val gate = CompletableDeferred<Result<Instant>>()
        withFixture {
            users.value = listOf(userRecord())
            deleteHandler = { gate.await() }
            runCurrent()

            viewModel.selectUserForDeletion("local-1")
            viewModel.confirmDelete()
            viewModel.confirmDelete()
            runCurrent()

            assertEquals(listOf("local-1"), deleteRequests)
            assertTrue(viewModel.uiState.value.deleteInProgress)

            gate.complete(Result.success(clock.current + 5.seconds))
            runCurrent()
            assertNull(viewModel.uiState.value.deleteConfirmation)
            assertTrue(!viewModel.uiState.value.deleteInProgress)
        }
    }

    @Test
    fun undoForCurrentSnackbarCallsRepositoryOnce() = runTest {
        withFixture {
            undoableDeletions.value = listOf(undoableDeletion())
            undoHandler = { localId ->
                undoableDeletions.value = emptyList()
                Result.success(Unit)
            }
            runCurrent()

            viewModel.undoDelete("local-1")
            viewModel.undoDelete("local-1")
            runCurrent()

            assertEquals(listOf("local-1"), undoRequests)
            assertNull(viewModel.uiState.value.undoSnackbar)
        }
    }

    @Test
    fun deadlineFinalizesDurableDeletionExactlyOnce() = runTest {
        withFixture {
            undoableDeletions.value = listOf(undoableDeletion())
            finalizeHandler = {
                undoableDeletions.value = emptyList()
                Result.success(1)
            }
            runCurrent()
            assertEquals("local-1", viewModel.uiState.value.undoSnackbar?.localId)

            clock.current += 5.seconds
            advanceTimeBy(5.seconds.inWholeMilliseconds)
            runCurrent()

            assertEquals(1, finalizeCalls)
            assertNull(viewModel.uiState.value.undoSnackbar)
        }
    }

    @Test
    fun multipleDeletionsShowNextOnlyAfterFirstIsFinalized() = runTest {
        withFixture {
            val first = undoableDeletion(localId = "local-1", name = "Ada", secondsFromNow = 5)
            val second = undoableDeletion(localId = "local-2", name = "Grace", secondsFromNow = 8)
            undoableDeletions.value = listOf(first, second)
            finalizeHandler = {
                undoableDeletions.value = undoableDeletions.value.drop(1)
                Result.success(1)
            }
            runCurrent()
            assertEquals("local-1", viewModel.uiState.value.undoSnackbar?.localId)

            clock.current += 5.seconds
            advanceTimeBy(5.seconds.inWholeMilliseconds)
            runCurrent()

            assertEquals(1, finalizeCalls)
            assertEquals("local-2", viewModel.uiState.value.undoSnackbar?.localId)
        }
    }

    private suspend fun kotlinx.coroutines.test.TestScope.withFixture(
        connectivityStatus: ConnectivityStatus = ConnectivityStatus.Available,
        initialRefreshHandler: suspend () -> Result<Unit> = { Result.success(Unit) },
        block: suspend Fixture.() -> Unit,
    ) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val fixture = Fixture(dispatcher, connectivityStatus, initialRefreshHandler)
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
        initialRefreshHandler: suspend () -> Result<Unit>,
    ) {
        val users = MutableStateFlow<List<UserRecord>>(emptyList())
        val undoableDeletions = MutableStateFlow<List<UndoableDeletion>>(emptyList())
        val syncState = MutableStateFlow<SyncState>(SyncState.Idle)
        val connectivity = FakeConnectivityObserver(connectivityStatus)
        val lifecycle = FakeLifecycleObserver()
        val clock = FakeTimeProvider(Instant.parse("2026-08-26T12:00:00Z"))
        var refreshCalls = 0
        var finalizeCalls = 0
        val deleteRequests = mutableListOf<String>()
        val undoRequests = mutableListOf<String>()
        var refreshHandler: suspend () -> Result<Unit> = initialRefreshHandler
        val addInputs = mutableListOf<AddUserInput>()
        var addHandler: suspend (AddUserInput) -> Result<String> = { Result.success("local-created") }
        val retryIds = mutableListOf<String>()
        var retryHandler: suspend (String) -> Result<Unit> = { Result.success(Unit) }
        var deleteHandler: suspend (String) -> Result<Instant> = {
            Result.success(clock.current + 5.seconds)
        }
        var undoHandler: suspend (String) -> Result<Unit> = { Result.success(Unit) }
        var finalizeHandler: suspend () -> Result<Int> = { Result.success(0) }
        val viewModel = UserFeedViewModel(
            observeUsers = ObserveUsers { users },
            observeSyncState = ObserveSyncState { syncState },
            observeUndoableDeletions = ObserveUndoableDeletions { undoableDeletions },
            refreshUsers = RefreshUsers {
                refreshCalls += 1
                refreshHandler()
            },
            addUser = AddUser { input ->
                addInputs += input
                addHandler(input)
            },
            retryUserCreationUseCase = RetryUserCreation { localId ->
                retryIds += localId
                retryHandler(localId)
            },
            addUserValidator = AddUserValidator(),
            deleteUserWithUndo = DeleteUserWithUndo { localId ->
                deleteRequests += localId
                deleteHandler(localId)
            },
            undoUserDeletion = UndoUserDeletion { localId ->
                undoRequests += localId
                undoHandler(localId)
            },
            finalizeExpiredDeletions = FinalizeExpiredDeletions {
                finalizeCalls += 1
                finalizeHandler()
            },
            connectivityObserver = connectivity,
            lifecycleObserver = lifecycle,
            timeProvider = clock,
            relativeTimeFormatter = RelativeTimeFormatter(),
            dispatcher = dispatcher,
        )
    }

    private class FakeConnectivityObserver(initial: ConnectivityStatus) : ConnectivityObserver {
        override val status = MutableStateFlow(initial)
    }

    private class FakeLifecycleObserver : AppLifecycleObserver {
        val mutableState = MutableStateFlow(AppLifecycleState.Background)
        override val state: StateFlow<AppLifecycleState> = mutableState
    }

    private class FakeTimeProvider(var current: Instant) : TimeProvider {
        override fun now(): Instant = current
    }

    private fun userRecord(
        observedAt: Instant = Instant.parse("2026-08-26T12:00:00Z"),
        localId: String = "local-1",
        name: String = "Ada Lovelace",
    ): UserRecord = UserRecord(
        user = User(
            localId = localId,
            remoteId = 42,
            name = name,
            email = "ada@example.com",
            gender = Gender.Female,
            status = UserStatus.Active,
            observedAt = observedAt,
        ),
        synchronization = UserSynchronization.Synced,
    )

    private fun Fixture.undoableDeletion(
        localId: String = "local-1",
        name: String = "Ada Lovelace",
        secondsFromNow: Int = 5,
    ): UndoableDeletion = UndoableDeletion(
        user = userRecord(localId = localId, name = name).user,
        deadline = clock.current + secondsFromNow.seconds,
    )
}
