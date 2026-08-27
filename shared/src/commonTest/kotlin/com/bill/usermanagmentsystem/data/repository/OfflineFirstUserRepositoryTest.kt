package com.bill.usermanagmentsystem.data.repository

import com.bill.usermanagmentsystem.data.local.MutationKind
import com.bill.usermanagmentsystem.data.local.MutationState
import com.bill.usermanagmentsystem.data.local.StoredMutation
import com.bill.usermanagmentsystem.data.local.StoredUser
import com.bill.usermanagmentsystem.data.local.StoredUserSyncStatus
import com.bill.usermanagmentsystem.data.remote.RemoteResult
import com.bill.usermanagmentsystem.data.remote.RemoteUser
import com.bill.usermanagmentsystem.data.testing.FakeSyncCoordinator
import com.bill.usermanagmentsystem.data.testing.FakeTimeProvider
import com.bill.usermanagmentsystem.data.testing.FakeUserLocalDataSource
import com.bill.usermanagmentsystem.data.testing.FakeUserRemoteDataSource
import com.bill.usermanagmentsystem.data.testing.QueueIdGenerator
import com.bill.usermanagmentsystem.domain.model.AddUserInput
import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.UserDataError
import com.bill.usermanagmentsystem.domain.model.UserStatus
import com.bill.usermanagmentsystem.domain.model.userDataErrorOrNull
import com.bill.usermanagmentsystem.domain.repository.PageLoadResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFirstUserRepositoryTest {
    @Test
    fun addInsertsTheUserOnlyAfterRemoteCreationSucceeds() =
        runTest {
            val local = FakeUserLocalDataSource()
            val sync = FakeSyncCoordinator()
            val remote =
                FakeUserRemoteDataSource().apply {
                    createHandler = { RemoteResult.Success(remoteUser(remoteId = 99)) }
                }
            val repository = repository(local, sync, remote)

            val result = repository.addUser(input())
            runCurrent()

            assertEquals("99", result.getOrNull())
            assertEquals(
                99,
                local.mergedPages
                    .single()
                    .single()
                    .remoteId,
            )
            assertEquals(0, sync.syncCalls)
        }

    @Test
    fun validationFailureDoesNotInsertALocalUser() =
        runTest {
            val local = FakeUserLocalDataSource()
            val sync = FakeSyncCoordinator()
            val remote =
                FakeUserRemoteDataSource().apply {
                    createHandler = { RemoteResult.ValidationFailure("email: has already been taken") }
                }
            val repository = repository(local, sync, remote)

            val result = repository.addUser(input())
            runCurrent()

            assertEquals(
                UserDataError.ValidationRejected("email: has already been taken"),
                result.exceptionOrNull()?.userDataErrorOrNull(),
            )
            assertTrue(local.mergedPages.isEmpty())
            assertEquals(0, sync.syncCalls)
        }

    @Test
    fun localWriteFailureIsTypedAndDoesNotTriggerSynchronization() =
        runTest {
            val local =
                FakeUserLocalDataSource().apply {
                    mergePageFailure = IllegalStateException("disk full")
                }
            val sync = FakeSyncCoordinator()
            val remote =
                FakeUserRemoteDataSource().apply {
                    createHandler = { RemoteResult.Success(remoteUser(remoteId = 99)) }
                }
            val repository = repository(local, sync, remote)

            val result = repository.addUser(input())
            runCurrent()

            assertEquals(
                UserDataError.Persistence("disk full"),
                result.exceptionOrNull()?.userDataErrorOrNull(),
            )
            assertEquals(0, sync.syncCalls)
        }

    @Test
    fun confirmedDeleteWaitsForRemoteSuccessBeforeRemovingTheLocalUser() =
        runTest {
            val local =
                FakeUserLocalDataSource().apply {
                    storedUsers["1"] = storedUser()
                }
            val remote =
                FakeUserRemoteDataSource().apply {
                    deleteHandler = { RemoteResult.Success(Unit) }
                }
            val repository = repository(local, FakeSyncCoordinator(), remote)

            val deleted = repository.deleteImmediately("1").getOrThrow()

            assertEquals(listOf(42L), remote.deleteRequests)
            assertEquals("Local user", deleted.userName)
            assertTrue("1" !in local.storedUsers)
        }

    @Test
    fun failedRemoteDeleteKeepsTheLocalUserVisible() =
        runTest {
            val local =
                FakeUserLocalDataSource().apply {
                    storedUsers["1"] = storedUser()
                }
            val remote =
                FakeUserRemoteDataSource().apply {
                    deleteHandler = { RemoteResult.RetryableFailure("Offline") }
                }

            val result = repository(local, FakeSyncCoordinator(), remote).deleteImmediately("1")

            assertTrue(result.isFailure)
            assertEquals(storedUser(), local.storedUsers["1"])
        }

    @Test
    fun undoRecreatesTheUserThroughPostAndMergesTheResponse() =
        runTest {
            val local = FakeUserLocalDataSource()
            val remote =
                FakeUserRemoteDataSource().apply {
                    createHandler = {
                        RemoteResult.Success(
                            RemoteUser(
                                remoteId = 99,
                                name = it.name,
                                email = it.email,
                                gender = it.gender,
                                status = it.status,
                                serverPosition = null,
                            ),
                        )
                    }
                }
            val repository = repository(local, FakeSyncCoordinator(), remote)

            assertEquals("99", repository.restoreDeletedUser(input()).getOrThrow())
            assertEquals(1, remote.createRequests.size)
            assertEquals(
                99,
                local.mergedPages
                    .single()
                    .single()
                    .remoteId,
            )
        }

    @Test
    fun expiredDeleteFinalizationTriggersSyncOnlyWhenStateChanged() =
        runTest {
            val local = FakeUserLocalDataSource().apply { finalizedDeleteResult = 1 }
            val sync = FakeSyncCoordinator()
            val repository = repository(local, sync)

            assertEquals(1, repository.finalizeExpiredDeletions().getOrThrow())
            runCurrent()

            assertEquals(1, local.finalizedDeleteCalls)
            assertEquals(1, sync.syncCalls)

            local.finalizedDeleteResult = 0
            assertEquals(0, repository.finalizeExpiredDeletions().getOrThrow())
            runCurrent()
            assertEquals(1, sync.syncCalls)
        }

    @Test
    fun explicitCreateRetryPersistsNewIntentBeforeSync() =
        runTest {
            val local = FakeUserLocalDataSource()
            val sync = FakeSyncCoordinator()
            val repository = repository(local, sync)

            assertTrue(repository.retryCreate("failed-user").isSuccess)
            runCurrent()

            val mutation = local.storedMutations.single()
            assertEquals("mutation-id", mutation.mutationId)
            assertEquals("failed-user", mutation.userLocalId)
            assertEquals(1, sync.syncCalls)
        }

    @Test
    fun explicitBlockedRetryResetsOnlyBlockedMutationsBeforeSync() =
        runTest {
            val local =
                FakeUserLocalDataSource().apply {
                    storedMutations += storedMutation("blocked", MutationState.Blocked)
                    storedMutations += storedMutation("waiting", MutationState.RetryableWait)
                }
            val sync = FakeSyncCoordinator()
            val repository = repository(local, sync)

            assertTrue(repository.retryBlockedSynchronization().isSuccess)
            runCurrent()

            assertEquals(listOf("blocked"), local.retriedBlockedMutations)
            assertEquals(1, sync.syncCalls)
        }

    @Test
    fun pageLoadingDelegatesToTheSharedCoordinator() =
        runTest {
            val sync =
                FakeSyncCoordinator().apply {
                    pageResult = Result.success(PageLoadResult(loadedCount = 20, hasMore = true))
                }
            val repository = repository(FakeUserLocalDataSource(), sync)

            val result = repository.loadNextPage().getOrThrow()

            assertEquals(PageLoadResult(loadedCount = 20, hasMore = true), result)
            assertEquals(1, sync.pageCalls)
        }

    private fun kotlinx.coroutines.test.TestScope.repository(
        local: FakeUserLocalDataSource,
        sync: FakeSyncCoordinator,
        remote: FakeUserRemoteDataSource = FakeUserRemoteDataSource(),
    ) = OfflineFirstUserRepository(
        localDataSource = local,
        remoteDataSource = remote,
        syncCoordinator = sync,
        idGenerator = QueueIdGenerator("mutation-id"),
        timeProvider = FakeTimeProvider(instant(1_000)),
        applicationScope = backgroundScope,
    )

    private companion object {
        fun instant(value: Long): Instant = Instant.fromEpochMilliseconds(value)

        fun input() =
            AddUserInput(
                name = "Local user",
                email = "local@example.com",
                gender = Gender.Female,
                status = UserStatus.Active,
            )

        fun remoteUser(remoteId: Long) =
            RemoteUser(
                remoteId = remoteId,
                name = "Local user",
                email = "local@example.com",
                gender = Gender.Female,
                status = UserStatus.Active,
                serverPosition = null,
            )

        fun storedUser() =
            StoredUser(
                localId = "1",
                remoteId = 42,
                name = "Local user",
                email = "local@example.com",
                gender = Gender.Female,
                status = UserStatus.Active,
                observedAt = instant(1_000),
                serverPosition = null,
                synchronization = StoredUserSyncStatus.Synced,
                hidden = false,
                undoDeadline = null,
                lastSyncError = null,
            )

        fun storedMutation(
            id: String,
            state: MutationState,
        ) = StoredMutation(
            mutationId = id,
            userLocalId = "user-$id",
            kind = MutationKind.Create,
            createdAt = instant(1_000),
            attemptCount = 0,
            state = state,
            retryAt = null,
            lastError = null,
        )
    }
}
