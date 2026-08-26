package com.bill.usermanagmentsystem.data.sync

import com.bill.usermanagmentsystem.data.local.DueMutation
import com.bill.usermanagmentsystem.data.local.MutationKind
import com.bill.usermanagmentsystem.data.local.SnapshotUser
import com.bill.usermanagmentsystem.data.local.UserLocalDataSource
import com.bill.usermanagmentsystem.data.remote.CreateUserRequest
import com.bill.usermanagmentsystem.data.remote.RemoteResult
import com.bill.usermanagmentsystem.data.remote.RemoteUser
import com.bill.usermanagmentsystem.data.remote.UserRemoteDataSource
import com.bill.usermanagmentsystem.domain.model.SyncState
import com.bill.usermanagmentsystem.domain.model.UserDataError
import com.bill.usermanagmentsystem.domain.model.UserDataException
import com.bill.usermanagmentsystem.platform.ConnectivityObserver
import com.bill.usermanagmentsystem.platform.ConnectivityStatus
import com.bill.usermanagmentsystem.platform.TimeProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class DefaultSyncCoordinator(
    private val localDataSource: UserLocalDataSource,
    private val remoteDataSource: UserRemoteDataSource,
    private val connectivityObserver: ConnectivityObserver,
    private val timeProvider: TimeProvider,
    private val retryPolicy: RetryPolicy,
    private val applicationScope: CoroutineScope,
) : SyncCoordinator {
    private val coordinationMutex = Mutex()
    private var activeRun: Deferred<Result<Unit>>? = null
    private val mutableState = MutableStateFlow<SyncState>(SyncState.Idle)

    override val state: StateFlow<SyncState> = mutableState.asStateFlow()

    override suspend fun sync(): Result<Unit> {
        val handle = coordinationMutex.withLock {
            val existing = activeRun?.takeUnless(Deferred<Result<Unit>>::isCompleted)
            if (existing != null) {
                ActiveRun(existing, created = false)
            } else {
                val created = applicationScope.async(start = CoroutineStart.LAZY) {
                    performSync()
                }
                activeRun = created
                ActiveRun(created, created = true)
            }
        }

        if (handle.created) {
            handle.deferred.invokeOnCompletion {
                applicationScope.launch {
                    coordinationMutex.withLock {
                        if (activeRun === handle.deferred) {
                            activeRun = null
                        }
                    }
                }
            }
            handle.deferred.start()
        }
        return handle.deferred.await()
    }

    private suspend fun performSync(): Result<Unit> {
        mutableState.value = SyncState.Running
        return try {
            performSyncSteps().also { result ->
                mutableState.value = result.fold(
                    onSuccess = { SyncState.Idle },
                    onFailure = { failure ->
                        SyncState.Failed(
                            (failure as? UserDataException)?.error
                                ?: UserDataError.Unexpected(failure.message ?: "Synchronization failed."),
                        )
                    },
                )
            }
        } catch (cancellation: CancellationException) {
            mutableState.value = SyncState.Idle
            throw cancellation
        } catch (failure: Throwable) {
            val exception = failure as? UserDataException ?: UserDataException(
                UserDataError.Unexpected(failure.message ?: "Synchronization failed unexpectedly."),
                failure,
            )
            mutableState.value = SyncState.Failed(exception.error)
            Result.failure(exception)
        }
    }

    private suspend fun performSyncSteps(): Result<Unit> {
        localDataSource.finalizeExpiredDeletes(timeProvider.now())
        if (!hasValidatedConnection()) return offlineFailure()

        var permanentFailure: UserDataException? = null
        val dueMutations = localDataSource.getDueMutations(timeProvider.now())
        for (mutation in dueMutations) {
            if (!hasValidatedConnection()) return offlineFailure()
            when (val result = processMutation(mutation)) {
                MutationResult.Completed -> Unit
                is MutationResult.CompletedWithFailure -> permanentFailure = permanentFailure ?: result.failure
                is MutationResult.Stop -> return Result.failure(result.failure)
            }
        }

        if (!hasValidatedConnection()) return offlineFailure()
        when (val snapshotResult = remoteDataSource.fetchLastPage()) {
            is RemoteResult.Success -> localDataSource.mergeSnapshot(
                users = snapshotResult.value.map(RemoteUser::toSnapshotUser),
                observedAt = timeProvider.now(),
            )

            is RemoteResult.RetryableFailure -> return retryableFailure(
                reason = snapshotResult.reason,
                retryAt = retryPolicy.nextRetryAt(
                    now = timeProvider.now(),
                    previousAttemptCount = 0,
                    serverRetryAt = snapshotResult.serverRetryAt,
                ),
            )

            RemoteResult.AuthenticationFailure -> return authenticationFailure()
            is RemoteResult.ValidationFailure -> return permanentRemoteFailure(snapshotResult.reason)
            RemoteResult.NotFound -> return permanentRemoteFailure("The user snapshot endpoint was not found.")
            is RemoteResult.PermanentFailure -> return permanentRemoteFailure(snapshotResult.reason)
        }

        return permanentFailure?.let(Result.Companion::failure) ?: Result.success(Unit)
    }

    private suspend fun processMutation(mutation: DueMutation): MutationResult = when (mutation.mutation.kind) {
        MutationKind.Create -> processCreate(mutation)
        MutationKind.Delete -> processDelete(mutation)
    }

    private suspend fun processCreate(mutation: DueMutation): MutationResult {
        val request = CreateUserRequest(
            name = mutation.name,
            email = mutation.email,
            gender = mutation.gender,
            status = mutation.status,
        )
        return when (val result = remoteDataSource.createUser(request)) {
            is RemoteResult.Success -> {
                localDataSource.completeCreate(
                    mutationId = mutation.mutation.mutationId,
                    localId = mutation.mutation.userLocalId,
                    remoteUser = result.value.toSnapshotUser(),
                )
                MutationResult.Completed
            }

            is RemoteResult.RetryableFailure -> scheduleRetry(mutation, result)
            RemoteResult.AuthenticationFailure -> blockForAuthentication(mutation)
            is RemoteResult.ValidationFailure -> {
                localDataSource.markCreateFailed(
                    mutationId = mutation.mutation.mutationId,
                    localId = mutation.mutation.userLocalId,
                    reason = result.reason,
                )
                MutationResult.CompletedWithFailure(
                    UserDataException(UserDataError.ValidationRejected(result.reason)),
                )
            }

            RemoteResult.NotFound -> blockPermanentMutation(
                mutation,
                "The create-user endpoint was not found.",
            )

            is RemoteResult.PermanentFailure -> blockPermanentMutation(mutation, result.reason)
        }
    }

    private suspend fun processDelete(mutation: DueMutation): MutationResult {
        val remoteId = mutation.remoteId
        if (remoteId == null) {
            localDataSource.completeDelete(
                mutationId = mutation.mutation.mutationId,
                localId = mutation.mutation.userLocalId,
            )
            return MutationResult.Completed
        }

        return when (val result = remoteDataSource.deleteUser(remoteId)) {
            is RemoteResult.Success,
            RemoteResult.NotFound,
            -> {
                localDataSource.completeDelete(
                    mutationId = mutation.mutation.mutationId,
                    localId = mutation.mutation.userLocalId,
                )
                MutationResult.Completed
            }

            is RemoteResult.RetryableFailure -> scheduleRetry(mutation, result)
            RemoteResult.AuthenticationFailure -> restorePermanentDeleteFailure(
                mutation = mutation,
                reason = "Authentication is required before deletion can continue.",
                error = UserDataError.AuthenticationRequired,
            )
            is RemoteResult.ValidationFailure -> restorePermanentDeleteFailure(mutation, result.reason)
            is RemoteResult.PermanentFailure -> restorePermanentDeleteFailure(mutation, result.reason)
        }
    }

    private suspend fun scheduleRetry(
        mutation: DueMutation,
        failure: RemoteResult.RetryableFailure,
    ): MutationResult {
        val retryAt = retryPolicy.nextRetryAt(
            now = timeProvider.now(),
            previousAttemptCount = mutation.mutation.attemptCount,
            serverRetryAt = failure.serverRetryAt,
        )
        localDataSource.markMutationRetryable(
            mutationId = mutation.mutation.mutationId,
            retryAt = retryAt,
            reason = failure.reason,
        )
        return MutationResult.Stop(
            UserDataException(UserDataError.RetryScheduled(failure.reason, retryAt)),
        )
    }

    private suspend fun blockForAuthentication(mutation: DueMutation): MutationResult {
        localDataSource.markMutationBlocked(
            mutationId = mutation.mutation.mutationId,
            reason = "Authentication is required.",
        )
        return MutationResult.Stop(UserDataException(UserDataError.AuthenticationRequired))
    }

    private suspend fun blockPermanentMutation(
        mutation: DueMutation,
        reason: String,
    ): MutationResult {
        localDataSource.markMutationBlocked(mutation.mutation.mutationId, reason)
        return MutationResult.Stop(UserDataException(UserDataError.RemoteContract(reason)))
    }

    private suspend fun restorePermanentDeleteFailure(
        mutation: DueMutation,
        reason: String,
        error: UserDataError = UserDataError.RemoteContract(reason),
    ): MutationResult {
        localDataSource.restoreAfterPermanentDeleteFailure(
            mutationId = mutation.mutation.mutationId,
            localId = mutation.mutation.userLocalId,
            reason = reason,
        )
        return MutationResult.CompletedWithFailure(
            UserDataException(error),
        )
    }

    private fun hasValidatedConnection(): Boolean =
        connectivityObserver.status.value == ConnectivityStatus.Available

    private fun offlineFailure(): Result<Unit> = Result.failure(UserDataException(UserDataError.Offline))

    private fun authenticationFailure(): Result<Unit> =
        Result.failure(UserDataException(UserDataError.AuthenticationRequired))

    private fun permanentRemoteFailure(reason: String): Result<Unit> =
        Result.failure(UserDataException(UserDataError.RemoteContract(reason)))

    private fun retryableFailure(
        reason: String,
        retryAt: kotlin.time.Instant,
    ): Result<Unit> = Result.failure(UserDataException(UserDataError.RetryScheduled(reason, retryAt)))

    private data class ActiveRun(
        val deferred: Deferred<Result<Unit>>,
        val created: Boolean,
    )

    private sealed interface MutationResult {
        data object Completed : MutationResult
        data class CompletedWithFailure(val failure: UserDataException) : MutationResult
        data class Stop(val failure: UserDataException) : MutationResult
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
