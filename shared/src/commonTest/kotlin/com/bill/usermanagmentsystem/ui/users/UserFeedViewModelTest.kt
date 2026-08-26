package com.bill.usermanagmentsystem.ui.users

import androidx.lifecycle.viewModelScope
import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.SyncState
import com.bill.usermanagmentsystem.domain.model.User
import com.bill.usermanagmentsystem.domain.model.UserDataError
import com.bill.usermanagmentsystem.domain.model.UserDataException
import com.bill.usermanagmentsystem.domain.model.UserRecord
import com.bill.usermanagmentsystem.domain.model.UserStatus
import com.bill.usermanagmentsystem.domain.model.UserSynchronization
import com.bill.usermanagmentsystem.domain.usecase.ObserveSyncState
import com.bill.usermanagmentsystem.domain.usecase.ObserveUsers
import com.bill.usermanagmentsystem.domain.usecase.RefreshUsers
import com.bill.usermanagmentsystem.domain.usecase.RelativeTimeFormatter
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
        val syncState = MutableStateFlow<SyncState>(SyncState.Idle)
        val connectivity = FakeConnectivityObserver(connectivityStatus)
        val lifecycle = FakeLifecycleObserver()
        val clock = FakeTimeProvider(Instant.parse("2026-08-26T12:00:00Z"))
        var refreshCalls = 0
        var refreshHandler: suspend () -> Result<Unit> = initialRefreshHandler
        val viewModel = UserFeedViewModel(
            observeUsers = ObserveUsers { users },
            observeSyncState = ObserveSyncState { syncState },
            refreshUsers = RefreshUsers {
                refreshCalls += 1
                refreshHandler()
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
        override val state: StateFlow<AppLifecycleState> =
            MutableStateFlow(AppLifecycleState.Background)
    }

    private class FakeTimeProvider(var current: Instant) : TimeProvider {
        override fun now(): Instant = current
    }

    private fun userRecord(
        observedAt: Instant = Instant.parse("2026-08-26T12:00:00Z"),
    ): UserRecord = UserRecord(
        user = User(
            localId = "local-1",
            remoteId = 42,
            name = "Ada Lovelace",
            email = "ada@example.com",
            gender = Gender.Female,
            status = UserStatus.Active,
            observedAt = observedAt,
        ),
        synchronization = UserSynchronization.Synced,
    )
}
