package com.bill.usermanagmentsystem.data.testing

import com.bill.usermanagmentsystem.data.local.DueMutation
import com.bill.usermanagmentsystem.data.local.IdGenerator
import com.bill.usermanagmentsystem.data.local.MutationKind
import com.bill.usermanagmentsystem.data.local.MutationState
import com.bill.usermanagmentsystem.data.local.SnapshotUser
import com.bill.usermanagmentsystem.data.local.StoredMutation
import com.bill.usermanagmentsystem.data.local.StoredUser
import com.bill.usermanagmentsystem.data.local.UserLocalDataSource
import com.bill.usermanagmentsystem.data.remote.CreateUserRequest
import com.bill.usermanagmentsystem.data.remote.RemoteResult
import com.bill.usermanagmentsystem.data.remote.RemoteUser
import com.bill.usermanagmentsystem.data.remote.UserRemoteDataSource
import com.bill.usermanagmentsystem.data.sync.SyncCoordinator
import com.bill.usermanagmentsystem.domain.model.AddUserInput
import com.bill.usermanagmentsystem.domain.model.SyncState
import com.bill.usermanagmentsystem.domain.model.UserRecord
import com.bill.usermanagmentsystem.platform.ConnectivityObserver
import com.bill.usermanagmentsystem.platform.ConnectivityStatus
import com.bill.usermanagmentsystem.platform.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

internal class FakeUserLocalDataSource : UserLocalDataSource {
    val visibleUsers = MutableStateFlow<List<UserRecord>>(emptyList())
    val storedUsers = mutableMapOf<String, StoredUser>()
    val storedMutations = mutableListOf<StoredMutation>()
    val dueMutations = mutableListOf<DueMutation>()
    val insertedCreates = mutableListOf<InsertedCreate>()
    val completedCreates = mutableListOf<Pair<String, String>>()
    val failedCreates = mutableListOf<Triple<String, String, String>>()
    val completedDeletes = mutableListOf<Pair<String, String>>()
    val restoredDeletes = mutableListOf<Triple<String, String, String>>()
    val retrySchedules = mutableListOf<RetrySchedule>()
    val blockedMutations = mutableListOf<Pair<String, String>>()
    val mergedSnapshots = mutableListOf<List<SnapshotUser>>()
    val deleteRequests = mutableListOf<Pair<String, Instant>>()
    val undoRequests = mutableListOf<Pair<String, Instant>>()
    val retriedBlockedMutations = mutableListOf<String>()
    var finalizedDeleteCalls = 0
    var insertFailure: Throwable? = null

    override fun observeVisibleUsers(): Flow<List<UserRecord>> = visibleUsers

    override suspend fun getUser(localId: String): StoredUser? = storedUsers[localId]

    override suspend fun getAllMutations(): List<StoredMutation> = storedMutations.toList()

    override suspend fun insertPendingCreate(
        localId: String,
        mutationId: String,
        input: AddUserInput,
        observedAt: Instant,
    ) {
        insertFailure?.let { throw it }
        insertedCreates += InsertedCreate(localId, mutationId, input, observedAt)
    }

    override suspend fun requestDelete(localId: String, undoDeadline: Instant) {
        deleteRequests += localId to undoDeadline
    }

    override suspend fun undoDelete(localId: String, now: Instant) {
        undoRequests += localId to now
    }

    override suspend fun finalizeExpiredDeletes(now: Instant): Int {
        finalizedDeleteCalls += 1
        return 0
    }

    override suspend fun getDueMutations(now: Instant): List<DueMutation> = dueMutations.toList()

    override suspend fun completeCreate(
        mutationId: String,
        localId: String,
        remoteUser: SnapshotUser,
    ) {
        completedCreates += mutationId to localId
    }

    override suspend fun markCreateFailed(mutationId: String, localId: String, reason: String) {
        failedCreates += Triple(mutationId, localId, reason)
    }

    override suspend fun retryFailedCreate(localId: String, mutationId: String, createdAt: Instant) {
        storedMutations += storedMutation(mutationId, localId, MutationKind.Create, createdAt)
    }

    override suspend fun completeDelete(mutationId: String, localId: String) {
        completedDeletes += mutationId to localId
    }

    override suspend fun restoreAfterPermanentDeleteFailure(
        mutationId: String,
        localId: String,
        reason: String,
    ) {
        restoredDeletes += Triple(mutationId, localId, reason)
    }

    override suspend fun markMutationRetryable(
        mutationId: String,
        retryAt: Instant,
        reason: String,
    ) {
        retrySchedules += RetrySchedule(mutationId, retryAt, reason)
    }

    override suspend fun markMutationBlocked(mutationId: String, reason: String) {
        blockedMutations += mutationId to reason
    }

    override suspend fun retryBlockedMutation(mutationId: String) {
        retriedBlockedMutations += mutationId
    }

    override suspend fun mergeSnapshot(users: List<SnapshotUser>, observedAt: Instant) {
        mergedSnapshots += users
    }
}

internal class FakeUserRemoteDataSource : UserRemoteDataSource {
    var fetchHandler: suspend () -> RemoteResult<List<RemoteUser>> = {
        RemoteResult.Success(emptyList())
    }
    var createHandler: suspend (CreateUserRequest) -> RemoteResult<RemoteUser> = {
        error("No create response configured.")
    }
    var deleteHandler: suspend (Long) -> RemoteResult<Unit> = {
        error("No delete response configured.")
    }
    var fetchCalls = 0
    val createRequests = mutableListOf<CreateUserRequest>()
    val deleteRequests = mutableListOf<Long>()
    val requestOrder = mutableListOf<String>()

    override suspend fun fetchLastPage(): RemoteResult<List<RemoteUser>> {
        fetchCalls += 1
        requestOrder += "FETCH"
        return fetchHandler()
    }

    override suspend fun createUser(request: CreateUserRequest): RemoteResult<RemoteUser> {
        createRequests += request
        requestOrder += "CREATE"
        return createHandler(request)
    }

    override suspend fun deleteUser(remoteId: Long): RemoteResult<Unit> {
        deleteRequests += remoteId
        requestOrder += "DELETE:$remoteId"
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

internal class QueueIdGenerator(
    private val ids: ArrayDeque<String>,
) : IdGenerator {
    constructor(vararg ids: String) : this(ArrayDeque(ids.toList()))

    override fun nextId(): String = ids.removeFirst()
}

internal class FakeSyncCoordinator(
    var result: Result<Unit> = Result.success(Unit),
) : SyncCoordinator {
    private val mutableState = MutableStateFlow<SyncState>(SyncState.Idle)
    override val state: StateFlow<SyncState> = mutableState
    var syncCalls = 0

    override suspend fun sync(): Result<Unit> {
        syncCalls += 1
        return result
    }
}

internal data class InsertedCreate(
    val localId: String,
    val mutationId: String,
    val input: AddUserInput,
    val observedAt: Instant,
)

internal data class RetrySchedule(
    val mutationId: String,
    val retryAt: Instant,
    val reason: String,
)

internal fun dueCreate(
    mutationId: String = "create-mutation",
    localId: String = "local-user",
    attemptCount: Long = 0,
): DueMutation = DueMutation(
    mutation = storedMutation(
        mutationId = mutationId,
        localId = localId,
        kind = MutationKind.Create,
        createdAt = Instant.fromEpochMilliseconds(1_000),
        attemptCount = attemptCount,
    ),
    remoteId = null,
    name = "Local user",
    email = "local@example.com",
    gender = com.bill.usermanagmentsystem.domain.model.Gender.Female,
    status = com.bill.usermanagmentsystem.domain.model.UserStatus.Active,
)

internal fun dueDelete(
    mutationId: String = "delete-mutation",
    localId: String = "local-user",
    remoteId: Long? = 99,
): DueMutation = DueMutation(
    mutation = storedMutation(
        mutationId = mutationId,
        localId = localId,
        kind = MutationKind.Delete,
        createdAt = Instant.fromEpochMilliseconds(1_000),
    ),
    remoteId = remoteId,
    name = "Remote user",
    email = "remote@example.com",
    gender = com.bill.usermanagmentsystem.domain.model.Gender.Male,
    status = com.bill.usermanagmentsystem.domain.model.UserStatus.Active,
)

private fun storedMutation(
    mutationId: String,
    localId: String,
    kind: MutationKind,
    createdAt: Instant,
    attemptCount: Long = 0,
): StoredMutation = StoredMutation(
    mutationId = mutationId,
    userLocalId = localId,
    kind = kind,
    createdAt = createdAt,
    attemptCount = attemptCount,
    state = MutationState.Pending,
    retryAt = null,
    lastError = null,
)
