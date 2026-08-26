package com.bill.usermanagmentsystem.data.repository

import com.bill.usermanagmentsystem.data.local.IdGenerator
import com.bill.usermanagmentsystem.data.local.MutationState
import com.bill.usermanagmentsystem.data.local.UserLocalDataSource
import com.bill.usermanagmentsystem.data.sync.SyncCoordinator
import com.bill.usermanagmentsystem.domain.model.AddUserInput
import com.bill.usermanagmentsystem.domain.model.SyncState
import com.bill.usermanagmentsystem.domain.model.UserDataError
import com.bill.usermanagmentsystem.domain.model.UserDataException
import com.bill.usermanagmentsystem.domain.model.UserRecord
import com.bill.usermanagmentsystem.domain.model.UndoableDeletion
import com.bill.usermanagmentsystem.domain.repository.UserRepository
import com.bill.usermanagmentsystem.platform.TimeProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.time.Instant

internal class OfflineFirstUserRepository(
    private val localDataSource: UserLocalDataSource,
    private val syncCoordinator: SyncCoordinator,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
    private val applicationScope: CoroutineScope,
) : UserRepository {
    override fun observeUsers(): Flow<List<UserRecord>> = localDataSource.observeVisibleUsers()

    override fun observeSyncState(): Flow<SyncState> = syncCoordinator.state

    override fun observeUndoableDeletions(): Flow<List<UndoableDeletion>> =
        localDataSource.observeUndoableUsers()

    override suspend fun refresh(): Result<Unit> = syncCoordinator.sync()

    override suspend fun addUser(input: AddUserInput): Result<String> {
        val result = durableOperation {
            val localId = idGenerator.nextId()
            localDataSource.insertPendingCreate(
                localId = localId,
                mutationId = idGenerator.nextId(),
                input = input,
                observedAt = timeProvider.now(),
            )
            localId
        }
        triggerSyncAfter(result)
        return result
    }

    override suspend fun requestDelete(
        localId: String,
        undoDeadline: Instant,
    ): Result<Unit> = durableOperation {
        localDataSource.requestDelete(localId, undoDeadline)
    }

    override suspend fun undoDelete(localId: String): Result<Unit> = durableOperation {
        localDataSource.undoDelete(localId, timeProvider.now())
    }

    override suspend fun finalizeExpiredDeletions(): Result<Int> {
        val result = durableOperation {
            localDataSource.finalizeExpiredDeletes(timeProvider.now())
        }
        if (result.getOrNull()?.let { it > 0 } == true) {
            triggerSyncAfter(result)
        }
        return result
    }

    override suspend fun retryCreate(localId: String): Result<Unit> {
        val result = durableOperation {
            localDataSource.retryFailedCreate(
                localId = localId,
                mutationId = idGenerator.nextId(),
                createdAt = timeProvider.now(),
            )
        }
        triggerSyncAfter(result)
        return result
    }

    override suspend fun retryBlockedSynchronization(): Result<Unit> {
        val result = durableOperation {
            localDataSource.getAllMutations()
                .filter { it.state == MutationState.Blocked }
                .forEach { localDataSource.retryBlockedMutation(it.mutationId) }
        }
        triggerSyncAfter(result)
        return result
    }

    override suspend fun syncPending(): Result<Unit> = syncCoordinator.sync()

    private fun triggerSyncAfter(result: Result<*>) {
        if (result.isSuccess) {
            applicationScope.launch { syncCoordinator.sync() }
        }
    }

    private suspend fun <T> durableOperation(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: UserDataException) {
        Result.failure(failure)
    } catch (failure: Throwable) {
        Result.failure(
            UserDataException(
                error = UserDataError.Persistence(
                    failure.message ?: "The local user state could not be updated.",
                ),
                cause = failure,
            ),
        )
    }
}
