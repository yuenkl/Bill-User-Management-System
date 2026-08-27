package com.bill.usermanagmentsystem.data.repository

import com.bill.usermanagmentsystem.data.local.SnapshotUser
import com.bill.usermanagmentsystem.data.local.UserLocalDataSource
import com.bill.usermanagmentsystem.data.remote.CreateUserRequest
import com.bill.usermanagmentsystem.data.remote.RemoteResult
import com.bill.usermanagmentsystem.data.remote.RemoteUser
import com.bill.usermanagmentsystem.data.remote.UserRemoteDataSource
import com.bill.usermanagmentsystem.domain.model.AddUserInput
import com.bill.usermanagmentsystem.domain.model.DeletedUserUndo
import com.bill.usermanagmentsystem.domain.model.UserDataError
import com.bill.usermanagmentsystem.domain.model.UserDataException
import com.bill.usermanagmentsystem.domain.model.UserRecord
import com.bill.usermanagmentsystem.domain.repository.PageLoadResult
import com.bill.usermanagmentsystem.domain.repository.UserRepository
import com.bill.usermanagmentsystem.platform.ConnectivityObserver
import com.bill.usermanagmentsystem.platform.ConnectivityStatus
import com.bill.usermanagmentsystem.platform.TimeProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class OfflineFirstUserRepository(
    private val localDataSource: UserLocalDataSource,
    private val remoteDataSource: UserRemoteDataSource,
    private val connectivityObserver: ConnectivityObserver,
    private val timeProvider: TimeProvider,
) : UserRepository {
    private val operationMutex = Mutex()
    private var nextPage: Long? = null

    override fun observeUsers(): Flow<List<UserRecord>> = localDataSource.observeUsers()

    override suspend fun refresh(): Result<Unit> =
        operationMutex.withLock {
            durableOperation {
                requireConnection()
                nextPage = null
                when (val result = remoteDataSource.fetchInitialPage()) {
                    is RemoteResult.Success -> {
                        localDataSource.mergeSnapshot(
                            users = result.value.users.map(RemoteUser::toSnapshotUser),
                            observedAt = timeProvider.now(),
                        )
                        nextPage = result.value.nextPage
                    }

                    else -> throw result.toUserDataException("The user directory could not be loaded.")
                }
            }
        }

    override suspend fun loadNextPage(): Result<PageLoadResult> =
        operationMutex.withLock {
            val page =
                nextPage
                    ?: return@withLock Result.success(PageLoadResult(loadedCount = 0, hasMore = false))
            durableOperation {
                requireConnection()
                when (val result = remoteDataSource.fetchPage(page)) {
                    is RemoteResult.Success -> {
                        localDataSource.mergePage(
                            users = result.value.users.map(RemoteUser::toSnapshotUser),
                            observedAt = timeProvider.now(),
                        )
                        nextPage = result.value.nextPage
                        PageLoadResult(
                            loadedCount = result.value.users.size,
                            hasMore = nextPage != null,
                        )
                    }

                    else -> throw result.toUserDataException("The next users page could not be loaded.")
                }
            }
        }

    override suspend fun addUser(input: AddUserInput): Result<String> =
        operationMutex.withLock {
            durableOperation {
                createUser(input)
            }
        }

    override suspend fun deleteImmediately(localId: String): Result<DeletedUserUndo> =
        operationMutex.withLock {
            durableOperation {
                val user =
                    localDataSource.getUser(localId)
                        ?: throw UserDataException(UserDataError.UserNotFound(localId))
                user.remoteId?.let { remoteId ->
                    when (val result = remoteDataSource.deleteUser(remoteId)) {
                        is RemoteResult.Success,
                        RemoteResult.NotFound,
                        -> Unit

                        else -> throw result.toUserDataException("The user could not be deleted.")
                    }
                }
                val deleted = localDataSource.deleteUser(localId)
                DeletedUserUndo(
                    userName = deleted.name,
                    input =
                        AddUserInput(
                            name = deleted.name,
                            email = deleted.email,
                            gender = deleted.gender,
                            status = deleted.status,
                        ),
                )
            }
        }

    override suspend fun restoreDeletedUser(input: AddUserInput): Result<String> =
        operationMutex.withLock {
            durableOperation {
                createUser(input)
            }
        }

    private suspend fun createUser(input: AddUserInput): String {
        requireConnection()
        return when (
            val result =
                remoteDataSource.createUser(
                    CreateUserRequest(
                        name = input.name,
                        email = input.email,
                        gender = input.gender,
                        status = input.status,
                    ),
                )
        ) {
            is RemoteResult.Success -> {
                localDataSource.mergePage(
                    users = listOf(result.value.toSnapshotUser()),
                    observedAt = timeProvider.now(),
                )
                result.value.remoteId.toString()
            }

            else -> throw result.toUserDataException("The user could not be saved.")
        }
    }

    private fun requireConnection() {
        if (connectivityObserver.status.value != ConnectivityStatus.Available) {
            throw UserDataException(UserDataError.Offline)
        }
    }

    private suspend fun <T> durableOperation(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: UserDataException) {
            Result.failure(failure)
        } catch (failure: Throwable) {
            Result.failure(
                UserDataException(
                    error =
                        UserDataError.Persistence(
                            failure.message ?: "The local user state could not be updated.",
                        ),
                    cause = failure,
                ),
            )
        }
}

private fun RemoteResult<*>.toUserDataException(notFoundMessage: String): UserDataException =
    when (this) {
        is RemoteResult.Success<*> -> error("A successful remote result cannot be converted to an error.")
        is RemoteResult.RetryableFailure -> UserDataException(UserDataError.RemoteContract(reason))
        RemoteResult.AuthenticationFailure -> UserDataException(UserDataError.AuthenticationRequired)
        is RemoteResult.ValidationFailure -> UserDataException(UserDataError.ValidationRejected(reason))
        RemoteResult.NotFound -> UserDataException(UserDataError.RemoteContract(notFoundMessage))
        is RemoteResult.PermanentFailure -> UserDataException(UserDataError.RemoteContract(reason))
    }

private fun RemoteUser.toSnapshotUser(): SnapshotUser =
    SnapshotUser(
        remoteId = remoteId,
        name = name,
        email = email,
        gender = gender,
        status = status,
        serverPosition = serverPosition,
    )
