package com.bill.usermanagmentsystem.data.repository

import com.bill.usermanagmentsystem.data.local.IdGenerator
import com.bill.usermanagmentsystem.data.local.MutationState
import com.bill.usermanagmentsystem.data.local.UserLocalDataSource
import com.bill.usermanagmentsystem.data.local.SnapshotUser
import com.bill.usermanagmentsystem.data.remote.CreateUserRequest
import com.bill.usermanagmentsystem.data.remote.RemoteResult
import com.bill.usermanagmentsystem.data.remote.RemoteUser
import com.bill.usermanagmentsystem.data.remote.UserRemoteDataSource
import com.bill.usermanagmentsystem.data.sync.SyncCoordinator
import com.bill.usermanagmentsystem.domain.model.AddUserInput
import com.bill.usermanagmentsystem.domain.model.DeletedUserUndo
import com.bill.usermanagmentsystem.domain.model.SyncState
import com.bill.usermanagmentsystem.domain.model.UserDataError
import com.bill.usermanagmentsystem.domain.model.UserDataException
import com.bill.usermanagmentsystem.domain.model.UserRecord
import com.bill.usermanagmentsystem.domain.model.UndoableDeletion
import com.bill.usermanagmentsystem.domain.repository.PageLoadResult
import com.bill.usermanagmentsystem.domain.repository.UserRepository
import com.bill.usermanagmentsystem.platform.TimeProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.time.Instant

internal class OfflineFirstUserRepository(
    private val localDataSource: UserLocalDataSource,
    private val remoteDataSource: UserRemoteDataSource,
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

    override suspend fun loadNextPage(): Result<PageLoadResult> = syncCoordinator.loadNextPage()

    override suspend fun addUser(input: AddUserInput): Result<String> {
        val result = durableOperation {
            localDataSource.insertPendingCreate(
                mutationId = idGenerator.nextId(),
                input = input,
                observedAt = timeProvider.now(),
            )
        }
        triggerSyncAfter(result)
        return result
    }

    override suspend fun deleteImmediately(localId: String): Result<DeletedUserUndo> = durableOperation {
        val user = localDataSource.getUser(localId)
            ?: throw UserDataException(UserDataError.UserNotFound(localId))
        user.remoteId?.let { remoteId ->
            when (val result = remoteDataSource.deleteUser(remoteId)) {
                is RemoteResult.Success,
                RemoteResult.NotFound,
                -> Unit
                RemoteResult.AuthenticationFailure -> throw UserDataException(UserDataError.AuthenticationRequired)
                is RemoteResult.RetryableFailure -> throw UserDataException(
                    UserDataError.RemoteContract(result.reason),
                )
                is RemoteResult.ValidationFailure -> throw UserDataException(UserDataError.RemoteContract(result.reason))
                is RemoteResult.PermanentFailure -> throw UserDataException(UserDataError.RemoteContract(result.reason))
            }
        }
        val deleted = localDataSource.deleteImmediately(localId)
        DeletedUserUndo(
            userName = deleted.name,
            input = AddUserInput(
                name = deleted.name,
                email = deleted.email,
                gender = deleted.gender,
                status = deleted.status,
            ),
        )
    }

    override suspend fun restoreDeletedUser(input: AddUserInput): Result<String> = durableOperation {
        when (val result = remoteDataSource.createUser(
            CreateUserRequest(
                name = input.name,
                email = input.email,
                gender = input.gender,
                status = input.status,
            ),
        )) {
            is RemoteResult.Success -> {
                localDataSource.mergePage(
                    users = listOf(result.value.toSnapshotUser()),
                    observedAt = timeProvider.now(),
                )
                result.value.remoteId.toString()
            }
            RemoteResult.AuthenticationFailure -> throw UserDataException(UserDataError.AuthenticationRequired)
            is RemoteResult.RetryableFailure -> throw UserDataException(UserDataError.RemoteContract(result.reason))
            is RemoteResult.ValidationFailure -> throw UserDataException(UserDataError.ValidationRejected(result.reason))
            RemoteResult.NotFound -> throw UserDataException(
                UserDataError.RemoteContract("The create-user endpoint was not found."),
            )
            is RemoteResult.PermanentFailure -> throw UserDataException(UserDataError.RemoteContract(result.reason))
        }
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

private fun RemoteUser.toSnapshotUser(): SnapshotUser = SnapshotUser(
    remoteId = remoteId,
    name = name,
    email = email,
    gender = gender,
    status = status,
    serverPosition = serverPosition,
)
