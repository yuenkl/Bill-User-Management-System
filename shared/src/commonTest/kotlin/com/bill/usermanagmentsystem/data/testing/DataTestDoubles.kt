package com.bill.usermanagmentsystem.data.testing

import com.bill.usermanagmentsystem.data.local.SnapshotUser
import com.bill.usermanagmentsystem.data.local.StoredUser
import com.bill.usermanagmentsystem.data.local.UserLocalDataSource
import com.bill.usermanagmentsystem.data.remote.CreateUserRequest
import com.bill.usermanagmentsystem.data.remote.RemotePage
import com.bill.usermanagmentsystem.data.remote.RemoteResult
import com.bill.usermanagmentsystem.data.remote.RemoteUser
import com.bill.usermanagmentsystem.data.remote.UserRemoteDataSource
import com.bill.usermanagmentsystem.domain.model.UserRecord
import com.bill.usermanagmentsystem.platform.ConnectivityObserver
import com.bill.usermanagmentsystem.platform.ConnectivityStatus
import com.bill.usermanagmentsystem.platform.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

internal class FakeUserLocalDataSource : UserLocalDataSource {
    val users = MutableStateFlow<List<UserRecord>>(emptyList())
    val storedUsers = mutableMapOf<String, StoredUser>()
    val mergedSnapshots = mutableListOf<List<SnapshotUser>>()
    val mergedPages = mutableListOf<List<SnapshotUser>>()
    var mergeSnapshotFailure: Throwable? = null
    var mergePageFailure: Throwable? = null

    override fun observeUsers(): Flow<List<UserRecord>> = users

    override suspend fun getUser(localId: String): StoredUser? = storedUsers[localId]

    override suspend fun deleteUser(localId: String): StoredUser = storedUsers.remove(localId) ?: error("No stored user for $localId")

    override suspend fun mergeSnapshot(
        users: List<SnapshotUser>,
        observedAt: Instant,
    ) {
        mergeSnapshotFailure?.let { throw it }
        mergedSnapshots += users
    }

    override suspend fun mergePage(
        users: List<SnapshotUser>,
        observedAt: Instant,
    ) {
        mergePageFailure?.let { throw it }
        mergedPages += users
    }
}

internal class FakeUserRemoteDataSource : UserRemoteDataSource {
    var fetchHandler: suspend () -> RemoteResult<List<RemoteUser>> = {
        RemoteResult.Success(emptyList())
    }
    var pageHandler: suspend (Long) -> RemoteResult<List<RemoteUser>> = {
        error("No page response configured.")
    }
    var totalPages: Long = 1
    var createHandler: suspend (CreateUserRequest) -> RemoteResult<RemoteUser> = {
        error("No create response configured.")
    }
    var deleteHandler: suspend (Long) -> RemoteResult<Unit> = {
        error("No delete response configured.")
    }
    var fetchCalls = 0
    val pageRequests = mutableListOf<Long>()
    val createRequests = mutableListOf<CreateUserRequest>()
    val deleteRequests = mutableListOf<Long>()

    override suspend fun fetchInitialPage(): RemoteResult<RemotePage> {
        fetchCalls += 1
        return fetchHandler().toRemotePage(page = 1, nextPage = 2L.takeIf { it <= totalPages })
    }

    override suspend fun fetchPage(page: Long): RemoteResult<RemotePage> {
        pageRequests += page
        return pageHandler(page).toRemotePage(page, (page + 1).takeIf { it <= totalPages })
    }

    override suspend fun createUser(request: CreateUserRequest): RemoteResult<RemoteUser> {
        createRequests += request
        return createHandler(request)
    }

    override suspend fun deleteUser(remoteId: Long): RemoteResult<Unit> {
        deleteRequests += remoteId
        return deleteHandler(remoteId)
    }
}

internal class FakeConnectivityObserver(
    initial: ConnectivityStatus = ConnectivityStatus.Available,
) : ConnectivityObserver {
    val mutableStatus = MutableStateFlow(initial)
    override val status: StateFlow<ConnectivityStatus> = mutableStatus
}

internal class FakeTimeProvider(
    var current: Instant,
) : TimeProvider {
    override fun now(): Instant = current
}

private fun RemoteResult<List<RemoteUser>>.toRemotePage(
    page: Long,
    nextPage: Long?,
): RemoteResult<RemotePage> =
    when (this) {
        is RemoteResult.Success -> RemoteResult.Success(RemotePage(value, page, nextPage))
        is RemoteResult.RetryableFailure -> RemoteResult.RetryableFailure(reason, serverRetryAt)
        RemoteResult.AuthenticationFailure -> RemoteResult.AuthenticationFailure
        is RemoteResult.ValidationFailure -> RemoteResult.ValidationFailure(reason)
        RemoteResult.NotFound -> RemoteResult.NotFound
        is RemoteResult.PermanentFailure -> RemoteResult.PermanentFailure(reason)
    }
