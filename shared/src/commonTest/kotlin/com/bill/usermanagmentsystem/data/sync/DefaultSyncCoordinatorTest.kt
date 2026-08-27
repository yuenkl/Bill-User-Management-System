package com.bill.usermanagmentsystem.data.sync

import com.bill.usermanagmentsystem.data.remote.RemoteResult
import com.bill.usermanagmentsystem.data.remote.RemoteUser
import com.bill.usermanagmentsystem.data.testing.FakeConnectivityObserver
import com.bill.usermanagmentsystem.data.testing.FakeTimeProvider
import com.bill.usermanagmentsystem.data.testing.FakeUserLocalDataSource
import com.bill.usermanagmentsystem.data.testing.FakeUserRemoteDataSource
import com.bill.usermanagmentsystem.data.testing.dueCreate
import com.bill.usermanagmentsystem.data.testing.dueDelete
import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.UserDataError
import com.bill.usermanagmentsystem.domain.model.UserDataException
import com.bill.usermanagmentsystem.domain.model.UserStatus
import com.bill.usermanagmentsystem.domain.model.userDataErrorOrNull
import com.bill.usermanagmentsystem.platform.ConnectivityStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSyncCoordinatorTest {
    @Test
    fun overlappingTriggersJoinOneRemoteRunWithoutQueuedSecondSync() = runTest {
        val fixture = fixture()
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        fixture.remote.fetchHandler = {
            fetchStarted.complete(Unit)
            releaseFetch.await()
            RemoteResult.Success(emptyList())
        }

        val first = async { fixture.coordinator.sync() }
        fetchStarted.await()
        val second = async { fixture.coordinator.sync() }
        runCurrent()

        assertEquals(1, fixture.remote.fetchCalls)
        first.cancelAndJoin()
        releaseFetch.complete(Unit)

        assertTrue(second.await().isSuccess)
        runCurrent()
        assertEquals(1, fixture.remote.fetchCalls)
    }

    @Test
    fun connectivityLossStopsBeforeNextFifoMutation() = runTest {
        val fixture = fixture()
        fixture.local.dueMutations += listOf(
            dueCreate(mutationId = "first", localId = "first-user"),
            dueCreate(mutationId = "second", localId = "second-user"),
        )
        fixture.remote.createHandler = { request ->
            fixture.connectivity.mutableStatus.value = ConnectivityStatus.Unavailable
            RemoteResult.Success(remoteUser(remoteId = 10, name = request.name))
        }

        val result = fixture.coordinator.sync()

        assertEquals(UserDataError.Offline, result.exceptionOrNull()?.userDataErrorOrNull())
        assertEquals(listOf("first" to "first-user"), fixture.local.completedCreates)
        assertEquals(1, fixture.remote.createRequests.size)
        assertEquals(0, fixture.remote.fetchCalls)
    }

    @Test
    fun mutationsRunFifoBeforeOneSnapshotRequest() = runTest {
        val fixture = fixture()
        fixture.local.dueMutations += listOf(
            dueCreate(mutationId = "first-create"),
            dueDelete(mutationId = "second-delete", remoteId = 77),
        )
        fixture.remote.createHandler = { RemoteResult.Success(remoteUser(remoteId = 10)) }
        fixture.remote.deleteHandler = { RemoteResult.Success(Unit) }

        assertTrue(fixture.coordinator.sync().isSuccess)

        assertEquals(listOf("CREATE", "DELETE:77", "FETCH"), fixture.remote.requestOrder)
    }

    @Test
    fun nextSyncRetriesOnlyAuthenticationBlockedMutationsAfterCredentialsChange() = runTest {
        val fixture = fixture()
        fixture.local.dueMutations += dueCreate(mutationId = "create", localId = "new-user")
        fixture.remote.createHandler = { RemoteResult.AuthenticationFailure }

        assertTrue(fixture.coordinator.sync().isFailure)
        assertEquals(listOf("create" to "Authentication is required."), fixture.local.blockedMutations)

        fixture.remote.createHandler = { RemoteResult.Success(remoteUser(remoteId = 55)) }

        assertTrue(fixture.coordinator.sync().isSuccess)
        assertEquals(2, fixture.local.retriedAuthenticationBlockedMutations)
        assertEquals(listOf("create" to "new-user"), fixture.local.completedCreates)
    }

    @Test
    fun successfulSyncStartsAtFirstPageAndLoadsFollowingPagesInOrder() = runTest {
        val fixture = fixture()
        fixture.remote.totalPages = 3
        fixture.remote.fetchHandler = {
            RemoteResult.Success(listOf(remoteUser(remoteId = 3, serverPosition = -60)))
        }
        fixture.remote.pageHandler = { page ->
            RemoteResult.Success(
                listOf(remoteUser(remoteId = page, serverPosition = -(page * 20))),
            )
        }

        assertTrue(fixture.coordinator.sync().isSuccess)
        val second = fixture.coordinator.loadNextPage().getOrThrow()
        val third = fixture.coordinator.loadNextPage().getOrThrow()
        val finished = fixture.coordinator.loadNextPage().getOrThrow()

        assertEquals(listOf(2L, 3L), fixture.remote.pageRequests)
        assertEquals(1, second.loadedCount)
        assertTrue(second.hasMore)
        assertTrue(!third.hasMore)
        assertEquals(0, finished.loadedCount)
        assertEquals(2, fixture.local.mergedPages.size)
    }

    @Test
    fun refreshResetsTheNextPageCursorFromTheInitialResponse() = runTest {
        val fixture = fixture()
        fixture.remote.totalPages = 148
        fixture.remote.fetchHandler = {
            RemoteResult.Success(listOf(remoteUser(remoteId = 148, serverPosition = -2_960)))
        }
        fixture.remote.pageHandler = { page ->
            RemoteResult.Success(listOf(remoteUser(remoteId = page, serverPosition = -(page * 20))))
        }

        assertTrue(fixture.coordinator.sync().isSuccess)
        assertTrue(fixture.coordinator.loadNextPage().isSuccess)

        fixture.remote.totalPages = 149
        assertTrue(fixture.coordinator.sync().isSuccess)
        assertTrue(fixture.coordinator.loadNextPage().isSuccess)

        assertEquals(listOf(2L, 2L), fixture.remote.pageRequests)
        assertEquals(2, fixture.local.mergedSnapshots.size)
    }

    @Test
    fun failedPageLoadDoesNotAdvanceTheCursor() = runTest {
        val fixture = fixture()
        fixture.remote.totalPages = 2
        var attempts = 0
        fixture.remote.pageHandler = {
            attempts += 1
            if (attempts == 1) {
                RemoteResult.RetryableFailure("HTTP 503")
            } else {
                RemoteResult.Success(listOf(remoteUser(remoteId = 1, serverPosition = -20)))
            }
        }

        assertTrue(fixture.coordinator.sync().isSuccess)
        assertTrue(fixture.coordinator.loadNextPage().isFailure)
        assertTrue(fixture.coordinator.loadNextPage().isSuccess)

        assertEquals(listOf(2L, 2L), fixture.remote.pageRequests)
        assertEquals(1, fixture.local.mergedPages.size)
    }

    @Test
    fun pageLoadingWaitsForActiveSyncAndUsesItsPublishedCursor() = runTest {
        val fixture = fixture()
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        fixture.remote.totalPages = 2
        fixture.remote.fetchHandler = {
            fetchStarted.complete(Unit)
            releaseFetch.await()
            RemoteResult.Success(listOf(remoteUser(remoteId = 2, serverPosition = -40)))
        }
        fixture.remote.pageHandler = { page ->
            RemoteResult.Success(listOf(remoteUser(remoteId = page, serverPosition = -20)))
        }

        val sync = async { fixture.coordinator.sync() }
        fetchStarted.await()
        val page = async { fixture.coordinator.loadNextPage() }
        runCurrent()

        assertTrue(fixture.remote.pageRequests.isEmpty())
        releaseFetch.complete(Unit)

        assertTrue(sync.await().isSuccess)
        assertTrue(page.await().isSuccess)
        assertEquals(listOf(2L), fixture.remote.pageRequests)
    }

    @Test
    fun retryableCreatePersistsBackoffAndStopsFifoProcessing() = runTest {
        val fixture = fixture(now = 1_000)
        fixture.local.dueMutations += listOf(
            dueCreate(mutationId = "retry-me", attemptCount = 1),
            dueCreate(mutationId = "wait-behind-me", localId = "later"),
        )
        fixture.remote.createHandler = { RemoteResult.RetryableFailure("HTTP 503") }

        val result = fixture.coordinator.sync()

        val error = assertIs<UserDataError.RetryScheduled>(result.exceptionOrNull()?.userDataErrorOrNull())
        assertEquals(instant(5_000), error.retryAt)
        assertEquals(instant(5_000), fixture.local.retrySchedules.single().retryAt)
        assertEquals(1, fixture.remote.createRequests.size)
        assertEquals(0, fixture.remote.fetchCalls)
    }

    @Test
    fun authenticationFailureBlocksMutationWithoutAutomaticLoop() = runTest {
        val fixture = fixture()
        fixture.local.dueMutations += dueCreate(mutationId = "blocked")
        fixture.remote.createHandler = { RemoteResult.AuthenticationFailure }

        val result = fixture.coordinator.sync()

        assertEquals(UserDataError.AuthenticationRequired, result.exceptionOrNull()?.userDataErrorOrNull())
        assertEquals("blocked", fixture.local.blockedMutations.single().first)
        assertEquals(0, fixture.remote.fetchCalls)
    }

    @Test
    fun validationFailureRemovesCreateIntentAndStillRefreshesSnapshot() = runTest {
        val fixture = fixture()
        fixture.local.dueMutations += dueCreate(mutationId = "invalid", localId = "invalid-user")
        fixture.remote.createHandler = { RemoteResult.ValidationFailure("Email is already taken") }

        val result = fixture.coordinator.sync()

        assertEquals(
            UserDataError.ValidationRejected("Email is already taken"),
            result.exceptionOrNull()?.userDataErrorOrNull(),
        )
        assertEquals(
            Triple("invalid", "invalid-user", "Email is already taken"),
            fixture.local.failedCreates.single(),
        )
        assertEquals(1, fixture.remote.fetchCalls)
    }

    @Test
    fun permanentCreateFailureIsBlockedAndDoesNotLoop() = runTest {
        val fixture = fixture()
        fixture.local.dueMutations += dueCreate(mutationId = "malformed")
        fixture.remote.createHandler = { RemoteResult.PermanentFailure("Malformed response") }

        val result = fixture.coordinator.sync()

        assertEquals(
            UserDataError.RemoteContract("Malformed response"),
            result.exceptionOrNull()?.userDataErrorOrNull(),
        )
        assertEquals("malformed", fixture.local.blockedMutations.single().first)
        assertEquals(0, fixture.remote.fetchCalls)
    }

    @Test
    fun deleteSuccessAndNotFoundBothCompleteDurableMutation() = runTest {
        listOf<RemoteResult<Unit>>(RemoteResult.Success(Unit), RemoteResult.NotFound).forEachIndexed {
                index,
                response,
            ->
            val fixture = fixture()
            fixture.local.dueMutations += dueDelete(
                mutationId = "delete-$index",
                localId = "user-$index",
                remoteId = index.toLong(),
            )
            fixture.remote.deleteHandler = { response }

            assertTrue(fixture.coordinator.sync().isSuccess)
            assertEquals(
                "delete-$index" to "user-$index",
                fixture.local.completedDeletes.single(),
            )
        }
    }

    @Test
    fun deleteWithoutRemoteIdCompletesLocallyWithoutNetwork() = runTest {
        val fixture = fixture()
        fixture.local.dueMutations += dueDelete(remoteId = null)

        assertTrue(fixture.coordinator.sync().isSuccess)

        assertEquals(1, fixture.local.completedDeletes.size)
        assertTrue(fixture.remote.deleteRequests.isEmpty())
    }

    @Test
    fun permanentDeleteFailureRestoresUserAndSurfacesReason() = runTest {
        val fixture = fixture()
        fixture.local.dueMutations += dueDelete(mutationId = "restore-delete", localId = "restore-user")
        fixture.remote.deleteHandler = { RemoteResult.PermanentFailure("Deletion is forbidden") }

        val result = fixture.coordinator.sync()

        assertEquals(
            UserDataError.RemoteContract("Deletion is forbidden"),
            result.exceptionOrNull()?.userDataErrorOrNull(),
        )
        assertEquals(
            Triple("restore-delete", "restore-user", "Deletion is forbidden"),
            fixture.local.restoredDeletes.single(),
        )
        assertEquals(1, fixture.remote.fetchCalls)
    }

    @Test
    fun retryableDeleteFailureKeepsDurableMutationScheduled() = runTest {
        val fixture = fixture(now = 1_000)
        fixture.local.dueMutations += dueDelete(
            mutationId = "retry-delete",
            localId = "hidden-user",
            remoteId = 77,
        )
        fixture.remote.deleteHandler = { RemoteResult.RetryableFailure("HTTP 503") }

        val result = fixture.coordinator.sync()

        assertIs<UserDataError.RetryScheduled>(result.exceptionOrNull()?.userDataErrorOrNull())
        assertEquals("retry-delete", fixture.local.retrySchedules.single().mutationId)
        assertTrue(fixture.local.completedDeletes.isEmpty())
        assertTrue(fixture.local.restoredDeletes.isEmpty())
    }

    @Test
    fun authenticationDeleteFailureRestoresUserAndRemovesAutomaticRetry() = runTest {
        val fixture = fixture()
        fixture.local.dueMutations += dueDelete(
            mutationId = "auth-delete",
            localId = "restore-user",
        )
        fixture.remote.deleteHandler = { RemoteResult.AuthenticationFailure }

        val result = fixture.coordinator.sync()

        assertEquals(UserDataError.AuthenticationRequired, result.exceptionOrNull()?.userDataErrorOrNull())
        assertEquals(
            Triple(
                "auth-delete",
                "restore-user",
                "Authentication is required before deletion can continue.",
            ),
            fixture.local.restoredDeletes.single(),
        )
        assertTrue(fixture.local.blockedMutations.isEmpty())
    }

    @Test
    fun emptyRemoteSnapshotIsCommittedAfterOutboxDrains() = runTest {
        val fixture = fixture()
        fixture.remote.fetchHandler = { RemoteResult.Success(emptyList()) }

        assertTrue(fixture.coordinator.sync().isSuccess)

        assertEquals(emptyList(), fixture.local.mergedSnapshots.single())
        assertEquals(1, fixture.local.finalizedDeleteCalls)
    }

    @Test
    fun permanentSnapshotFailureKeepsLocalSnapshotUntouched() = runTest {
        val fixture = fixture()
        fixture.remote.fetchHandler = { RemoteResult.PermanentFailure("Invalid pagination") }

        val result = fixture.coordinator.sync()

        assertEquals(
            UserDataError.RemoteContract("Invalid pagination"),
            result.exceptionOrNull()?.userDataErrorOrNull(),
        )
        assertTrue(fixture.local.mergedSnapshots.isEmpty())
    }

    @Test
    fun offlineRunStillFinalizesExpiredLocalDeletesBeforeStopping() = runTest {
        val fixture = fixture(connectivityStatus = ConnectivityStatus.Unavailable)

        val result = fixture.coordinator.sync()

        assertEquals(UserDataError.Offline, result.exceptionOrNull()?.userDataErrorOrNull())
        assertEquals(1, fixture.local.finalizedDeleteCalls)
        assertEquals(0, fixture.remote.fetchCalls)
    }

    private fun kotlinx.coroutines.test.TestScope.fixture(
        now: Long = 1_000,
        connectivityStatus: ConnectivityStatus = ConnectivityStatus.Available,
    ): Fixture {
        val local = FakeUserLocalDataSource()
        val remote = FakeUserRemoteDataSource()
        val connectivity = FakeConnectivityObserver(connectivityStatus)
        val coordinator = DefaultSyncCoordinator(
            localDataSource = local,
            remoteDataSource = remote,
            connectivityObserver = connectivity,
            timeProvider = FakeTimeProvider(instant(now)),
            retryPolicy = RetryPolicy(),
            applicationScope = backgroundScope,
        )
        return Fixture(local, remote, connectivity, coordinator)
    }

    private data class Fixture(
        val local: FakeUserLocalDataSource,
        val remote: FakeUserRemoteDataSource,
        val connectivity: FakeConnectivityObserver,
        val coordinator: DefaultSyncCoordinator,
    )

    private companion object {
        fun instant(value: Long): Instant = Instant.fromEpochMilliseconds(value)

        fun remoteUser(
            remoteId: Long,
            name: String = "Remote user",
            serverPosition: Long = 0,
        ) = RemoteUser(
            remoteId = remoteId,
            name = name,
            email = "remote@example.com",
            gender = Gender.Female,
            status = UserStatus.Active,
            serverPosition = serverPosition,
        )
    }
}
