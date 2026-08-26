package com.bill.usermanagmentsystem.data.repository

import com.bill.usermanagmentsystem.data.local.MutationKind
import com.bill.usermanagmentsystem.data.local.MutationState
import com.bill.usermanagmentsystem.data.local.StoredMutation
import com.bill.usermanagmentsystem.data.testing.FakeSyncCoordinator
import com.bill.usermanagmentsystem.data.testing.FakeTimeProvider
import com.bill.usermanagmentsystem.data.testing.FakeUserLocalDataSource
import com.bill.usermanagmentsystem.data.testing.QueueIdGenerator
import com.bill.usermanagmentsystem.domain.model.AddUserInput
import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.UserDataError
import com.bill.usermanagmentsystem.domain.model.UserStatus
import com.bill.usermanagmentsystem.domain.model.userDataErrorOrNull
import com.bill.usermanagmentsystem.domain.usecase.DefaultDeleteUserWithUndo
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
    fun addCommitsLocalIntentBeforeTriggeringSynchronization() = runTest {
        val local = FakeUserLocalDataSource()
        val sync = FakeSyncCoordinator()
        val repository = repository(local, sync)

        val result = repository.addUser(input())
        runCurrent()

        assertEquals("local-id", result.getOrNull())
        assertEquals("local-id", local.insertedCreates.single().localId)
        assertEquals("mutation-id", local.insertedCreates.single().mutationId)
        assertEquals(1, sync.syncCalls)
    }

    @Test
    fun remoteSyncFailureDoesNotRollBackSuccessfulLocalAdd() = runTest {
        val local = FakeUserLocalDataSource()
        val sync = FakeSyncCoordinator(Result.failure(IllegalStateException("offline")))
        val repository = repository(local, sync)

        val result = repository.addUser(input())
        runCurrent()

        assertEquals("local-id", result.getOrNull())
        assertEquals(1, local.insertedCreates.size)
        assertEquals(1, sync.syncCalls)
    }

    @Test
    fun localWriteFailureIsTypedAndDoesNotTriggerSynchronization() = runTest {
        val local = FakeUserLocalDataSource().apply {
            insertFailure = IllegalStateException("disk full")
        }
        val sync = FakeSyncCoordinator()
        val repository = repository(local, sync)

        val result = repository.addUser(input())
        runCurrent()

        assertEquals(
            UserDataError.Persistence("disk full"),
            result.exceptionOrNull()?.userDataErrorOrNull(),
        )
        assertEquals(0, sync.syncCalls)
    }

    @Test
    fun deleteAndUndoUseDurableLocalStateAndInjectedClock() = runTest {
        val local = FakeUserLocalDataSource()
        val repository = repository(local, FakeSyncCoordinator())
        val deadline = instant(6_000)

        assertTrue(repository.requestDelete("user-id", deadline).isSuccess)
        assertTrue(repository.undoDelete("user-id").isSuccess)

        assertEquals("user-id" to deadline, local.deleteRequests.single())
        assertEquals("user-id" to instant(1_000), local.undoRequests.single())
    }

    @Test
    fun deleteUseCaseCalculatesExactFiveSecondDeadline() = runTest {
        val local = FakeUserLocalDataSource()
        val repository = repository(local, FakeSyncCoordinator())
        val useCase = DefaultDeleteUserWithUndo(
            repository = repository,
            timeProvider = FakeTimeProvider(instant(1_000)),
        )

        val result = useCase("user-id")

        assertEquals(instant(6_000), result.getOrThrow())
        assertEquals("user-id" to instant(6_000), local.deleteRequests.single())
    }

    @Test
    fun expiredDeleteFinalizationTriggersSyncOnlyWhenStateChanged() = runTest {
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
    fun explicitCreateRetryPersistsNewIntentBeforeSync() = runTest {
        val local = FakeUserLocalDataSource()
        val sync = FakeSyncCoordinator()
        val repository = repository(local, sync)

        assertTrue(repository.retryCreate("failed-user").isSuccess)
        runCurrent()

        val mutation = local.storedMutations.single()
        assertEquals("local-id", mutation.mutationId)
        assertEquals("failed-user", mutation.userLocalId)
        assertEquals(1, sync.syncCalls)
    }

    @Test
    fun explicitBlockedRetryResetsOnlyBlockedMutationsBeforeSync() = runTest {
        val local = FakeUserLocalDataSource().apply {
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

    private fun kotlinx.coroutines.test.TestScope.repository(
        local: FakeUserLocalDataSource,
        sync: FakeSyncCoordinator,
    ) = OfflineFirstUserRepository(
        localDataSource = local,
        syncCoordinator = sync,
        idGenerator = QueueIdGenerator("local-id", "mutation-id"),
        timeProvider = FakeTimeProvider(instant(1_000)),
        applicationScope = backgroundScope,
    )

    private companion object {
        fun instant(value: Long): Instant = Instant.fromEpochMilliseconds(value)

        fun input() = AddUserInput(
            name = "Local user",
            email = "local@example.com",
            gender = Gender.Female,
            status = UserStatus.Active,
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
