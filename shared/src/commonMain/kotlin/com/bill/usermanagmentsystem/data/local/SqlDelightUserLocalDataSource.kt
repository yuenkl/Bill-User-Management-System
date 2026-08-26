package com.bill.usermanagmentsystem.data.local

import com.bill.usermanagmentsystem.data.local.db.Pending_mutations
import com.bill.usermanagmentsystem.data.local.db.SelectDueMutations
import com.bill.usermanagmentsystem.data.local.db.SelectUndoableUsers
import com.bill.usermanagmentsystem.data.local.db.UserManagementDatabase
import com.bill.usermanagmentsystem.data.local.db.Users
import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.User
import com.bill.usermanagmentsystem.domain.model.UserDataError
import com.bill.usermanagmentsystem.domain.model.UserDataException
import com.bill.usermanagmentsystem.domain.model.UserRecord
import com.bill.usermanagmentsystem.domain.model.UserStatus
import com.bill.usermanagmentsystem.domain.model.UserSynchronization
import com.bill.usermanagmentsystem.domain.model.UndoableDeletion
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlin.time.Instant

internal class SqlDelightUserLocalDataSource(
    database: UserManagementDatabase,
    private val idGenerator: IdGenerator,
    private val queryDispatcher: CoroutineDispatcher,
) : UserLocalDataSource {
    private val queries = database.userManagementDatabaseQueries

    override fun observeVisibleUsers(): Flow<List<UserRecord>> =
        queries.selectVisibleUsers()
            .asFlow()
            .mapToList(queryDispatcher)
            .map { rows -> rows.map(Users::toDomainRecord) }

    override fun observeUndoableUsers(): Flow<List<UndoableDeletion>> =
        queries.selectUndoableUsers()
            .asFlow()
            .mapToList(queryDispatcher)
            .map { rows -> rows.map(SelectUndoableUsers::toUndoableDeletion) }

    override suspend fun getUser(localId: String): StoredUser? = withContext(queryDispatcher) {
        queries.selectUserByLocalId(localId.toDatabaseLocalId()).executeAsOneOrNull()?.toStoredUser()
    }

    override suspend fun getAllMutations(): List<StoredMutation> = withContext(queryDispatcher) {
        queries.selectAllMutations().executeAsList().map(Pending_mutations::toStoredMutation)
    }

    override suspend fun insertPendingCreate(
        mutationId: String,
        input: com.bill.usermanagmentsystem.domain.model.AddUserInput,
        observedAt: Instant,
    ): String = withContext(queryDispatcher) {
        var localId: Long? = null
        queries.transaction {
            queries.insertPendingUser(
                name = input.name,
                email = input.email,
                gender = input.gender.apiValue,
                status = input.status.apiValue,
                observed_at_epoch_ms = observedAt.toEpochMilliseconds(),
            )
            localId = queries.selectLastInsertedUserId().executeAsOne()
            queries.insertMutation(
                mutation_id = mutationId,
                user_local_id = requireNotNull(localId),
                kind = MutationKind.Create.databaseValue,
                created_at_epoch_ms = observedAt.toEpochMilliseconds(),
            )
        }
        requireNotNull(localId).toString()
    }

    override suspend fun requestDelete(
        localId: String,
        undoDeadline: Instant,
    ) = withContext(queryDispatcher) {
        queries.transaction {
            val databaseLocalId = localId.toDatabaseLocalId()
            val user = queries.selectUserByLocalId(databaseLocalId).executeAsOneOrNull()
                ?: throw UserDataException(UserDataError.UserNotFound(localId))

            when (user.syncStatus()) {
                StoredUserSyncStatus.PendingCreate,
                StoredUserSyncStatus.CreateFailed,
                -> {
                    queries.deleteMutationForUserKind(databaseLocalId, MutationKind.Create.databaseValue)
                    queries.deleteUser(databaseLocalId)
                }

                StoredUserSyncStatus.Synced -> {
                    if (!user.isHidden()) {
                        queries.hideSyncedUser(undoDeadline.toEpochMilliseconds(), databaseLocalId)
                    }
                }

                StoredUserSyncStatus.PendingDelete -> Unit
            }
        }
    }

    override suspend fun undoDelete(
        localId: String,
        now: Instant,
    ) = withContext(queryDispatcher) {
        queries.transaction {
            val databaseLocalId = localId.toDatabaseLocalId()
            val user = queries.selectUserByLocalId(databaseLocalId).executeAsOneOrNull()
                ?: throw UserDataException(UserDataError.UserNotFound(localId))
            val deadline = user.undo_deadline_epoch_ms?.let(Instant::fromEpochMilliseconds)

            if (
                user.syncStatus() != StoredUserSyncStatus.Synced ||
                !user.isHidden() ||
                deadline == null ||
                now >= deadline
            ) {
                throw UserDataException(UserDataError.DeleteTooLate)
            }
            queries.restoreUndoableUser(databaseLocalId)
        }
    }

    override suspend fun finalizeExpiredDeletes(now: Instant): Int = withContext(queryDispatcher) {
        var finalizedCount = 0
        queries.transaction {
            val localIds = queries.selectExpiredUndoableUsers(now.toEpochMilliseconds()).executeAsList()
            localIds.forEach { localId ->
                queries.insertMutation(
                    mutation_id = idGenerator.nextId(),
                    user_local_id = localId,
                    kind = MutationKind.Delete.databaseValue,
                    created_at_epoch_ms = now.toEpochMilliseconds(),
                )
                queries.markPendingDelete(localId)
                finalizedCount += 1
            }
        }
        finalizedCount
    }

    override suspend fun getDueMutations(now: Instant): List<DueMutation> =
        withContext(queryDispatcher) {
            queries.selectDueMutations(now.toEpochMilliseconds())
                .executeAsList()
                .map(SelectDueMutations::toDueMutation)
        }

    override suspend fun completeCreate(
        mutationId: String,
        localId: String,
        remoteUser: SnapshotUser,
    ) = withContext(queryDispatcher) {
        queries.transaction {
            queries.completeCreate(
                remote_id = remoteUser.remoteId,
                name = remoteUser.name,
                email = remoteUser.email,
                gender = remoteUser.gender.apiValue,
                status = remoteUser.status.apiValue,
                server_position = remoteUser.serverPosition,
                local_id = localId.toDatabaseLocalId(),
            ).requireSingleUpdate("complete CREATE for $localId")
            queries.deleteMutation(mutationId)
        }
    }

    override suspend fun markCreateFailed(
        mutationId: String,
        localId: String,
        reason: String,
    ) = withContext(queryDispatcher) {
        queries.transaction {
            queries.markCreateFailed(reason, localId.toDatabaseLocalId())
                .requireSingleUpdate("mark CREATE failed for $localId")
            queries.deleteMutation(mutationId)
        }
    }

    override suspend fun retryFailedCreate(
        localId: String,
        mutationId: String,
        createdAt: Instant,
    ) = withContext(queryDispatcher) {
        queries.transaction {
            queries.retryCreate(localId.toDatabaseLocalId())
                .requireSingleUpdate("retry CREATE for $localId")
            queries.insertMutation(
                mutation_id = mutationId,
                user_local_id = localId.toDatabaseLocalId(),
                kind = MutationKind.Create.databaseValue,
                created_at_epoch_ms = createdAt.toEpochMilliseconds(),
            )
        }
    }

    override suspend fun completeDelete(
        mutationId: String,
        localId: String,
    ) = withContext(queryDispatcher) {
        queries.transaction {
            queries.deleteMutation(mutationId)
            queries.deleteUser(localId.toDatabaseLocalId())
        }
    }

    override suspend fun restoreAfterPermanentDeleteFailure(
        mutationId: String,
        localId: String,
        reason: String,
    ) = withContext(queryDispatcher) {
        queries.transaction {
            queries.restoreFailedDelete(reason, localId.toDatabaseLocalId())
                .requireSingleUpdate("restore DELETE failure for $localId")
            queries.deleteMutation(mutationId)
        }
    }

    override suspend fun markMutationRetryable(
        mutationId: String,
        retryAt: Instant,
        reason: String,
    ) = withContext(queryDispatcher) {
        queries.updateMutationRetryable(retryAt.toEpochMilliseconds(), reason, mutationId)
            .requireSingleUpdate("schedule retry for mutation $mutationId")
    }

    override suspend fun markMutationBlocked(
        mutationId: String,
        reason: String,
    ) = withContext(queryDispatcher) {
        queries.updateMutationBlocked(reason, mutationId)
            .requireSingleUpdate("block mutation $mutationId")
    }

    override suspend fun retryBlockedMutation(mutationId: String) = withContext(queryDispatcher) {
        queries.resetMutationForExplicitRetry(mutationId)
            .requireSingleUpdate("retry blocked mutation $mutationId")
    }

    override suspend fun retryAuthenticationBlockedMutations() = withContext(queryDispatcher) {
        queries.retryAuthenticationBlockedMutations().await()
        Unit
    }

    override suspend fun mergeSnapshot(
        users: List<SnapshotUser>,
        observedAt: Instant,
    ) = withContext(queryDispatcher) {
        queries.transaction {
            val snapshotRemoteIds = users.mapTo(mutableSetOf(), SnapshotUser::remoteId)
            mergeUsers(users, observedAt)
            queries.selectSyncedRemoteUsers().executeAsList()
                .filterNot { it.remote_id in snapshotRemoteIds }
                .forEach { queries.deleteUser(it.local_id) }
        }
    }

    override suspend fun mergePage(
        users: List<SnapshotUser>,
        observedAt: Instant,
    ) = withContext(queryDispatcher) {
        queries.transaction {
            mergeUsers(users, observedAt)
        }
    }

    private fun mergeUsers(
        users: List<SnapshotUser>,
        observedAt: Instant,
    ) {
        users.forEach { user ->
            queries.insertRemoteUserIfAbsent(
                remote_id = user.remoteId,
                name = user.name,
                email = user.email,
                gender = user.gender.apiValue,
                status = user.status.apiValue,
                observed_at_epoch_ms = observedAt.toEpochMilliseconds(),
                server_position = user.serverPosition,
            )
            queries.updatePagedRemoteUser(
                name = user.name,
                email = user.email,
                gender = user.gender.apiValue,
                status = user.status.apiValue,
                server_position = user.serverPosition,
                remote_id = user.remoteId,
            )
            queries.updateLocallyCreatedUser(
                name = user.name,
                email = user.email,
                gender = user.gender.apiValue,
                status = user.status.apiValue,
                remote_id = user.remoteId,
            )
        }
    }
}

private val MutationKind.databaseValue: String
    get() = when (this) {
        MutationKind.Create -> "CREATE"
        MutationKind.Delete -> "DELETE"
    }

private fun String.toMutationKind(): MutationKind = when (this) {
    "CREATE" -> MutationKind.Create
    "DELETE" -> MutationKind.Delete
    else -> persistenceFailure("Unknown mutation kind: $this")
}

private fun String.toMutationState(): MutationState = when (this) {
    "PENDING" -> MutationState.Pending
    "RETRYABLE_WAIT" -> MutationState.RetryableWait
    "BLOCKED" -> MutationState.Blocked
    else -> persistenceFailure("Unknown mutation state: $this")
}

private fun String.toStoredSyncStatus(): StoredUserSyncStatus = when (this) {
    "SYNCED" -> StoredUserSyncStatus.Synced
    "PENDING_CREATE" -> StoredUserSyncStatus.PendingCreate
    "CREATE_FAILED" -> StoredUserSyncStatus.CreateFailed
    "PENDING_DELETE" -> StoredUserSyncStatus.PendingDelete
    else -> persistenceFailure("Unknown user synchronization state: $this")
}

private fun String.toGender(): Gender = Gender.entries.firstOrNull { it.apiValue == this }
    ?: persistenceFailure("Unknown gender: $this")

private fun String.toUserStatus(): UserStatus = UserStatus.entries.firstOrNull { it.apiValue == this }
    ?: persistenceFailure("Unknown user status: $this")

private fun Users.toStoredUser(): StoredUser = StoredUser(
    localId = local_id.toString(),
    remoteId = remote_id,
    name = name,
    email = email,
    gender = gender.toGender(),
    status = status.toUserStatus(),
    observedAt = Instant.fromEpochMilliseconds(observed_at_epoch_ms),
    serverPosition = server_position,
    synchronization = syncStatus(),
    hidden = isHidden(),
    undoDeadline = undo_deadline_epoch_ms?.let(Instant::fromEpochMilliseconds),
    lastSyncError = last_sync_error,
)

private fun Users.toDomainRecord(): UserRecord {
    val synchronization = when (syncStatus()) {
        StoredUserSyncStatus.Synced -> UserSynchronization.Synced
        StoredUserSyncStatus.PendingCreate -> UserSynchronization.PendingCreate
        StoredUserSyncStatus.CreateFailed -> UserSynchronization.CreateFailed(
            reason = last_sync_error ?: "Creation requires review before retrying.",
        )
        StoredUserSyncStatus.PendingDelete -> persistenceFailure(
            "Pending-delete user $local_id escaped the visible query.",
        )
    }
    return UserRecord(
        user = User(
            localId = local_id.toString(),
            remoteId = remote_id,
            name = name,
            email = email,
            gender = gender.toGender(),
            status = status.toUserStatus(),
            observedAt = Instant.fromEpochMilliseconds(observed_at_epoch_ms),
        ),
        synchronization = synchronization,
    )
}

private fun SelectUndoableUsers.toUndoableDeletion(): UndoableDeletion = UndoableDeletion(
    user = User(
        localId = local_id.toString(),
        remoteId = remote_id,
        name = name,
        email = email,
        gender = gender.toGender(),
        status = status.toUserStatus(),
        observedAt = Instant.fromEpochMilliseconds(observed_at_epoch_ms),
    ),
    deadline = Instant.fromEpochMilliseconds(undo_deadline_epoch_ms),
)

private fun Pending_mutations.toStoredMutation(): StoredMutation = StoredMutation(
    mutationId = mutation_id,
    userLocalId = user_local_id.toString(),
    kind = kind.toMutationKind(),
    createdAt = Instant.fromEpochMilliseconds(created_at_epoch_ms),
    attemptCount = attempt_count,
    state = state.toMutationState(),
    retryAt = retry_at_epoch_ms?.let(Instant::fromEpochMilliseconds),
    lastError = last_error,
)

private fun SelectDueMutations.toDueMutation(): DueMutation = DueMutation(
    mutation = StoredMutation(
        mutationId = mutation_id,
        userLocalId = user_local_id.toString(),
        kind = kind.toMutationKind(),
        createdAt = Instant.fromEpochMilliseconds(created_at_epoch_ms),
        attemptCount = attempt_count,
        state = state.toMutationState(),
        retryAt = retry_at_epoch_ms?.let(Instant::fromEpochMilliseconds),
        lastError = last_error,
    ),
    remoteId = remote_id,
    name = name,
    email = email,
    gender = gender.toGender(),
    status = status.toUserStatus(),
)

private fun Users.syncStatus(): StoredUserSyncStatus = sync_status.toStoredSyncStatus()

private fun Users.isHidden(): Boolean = hidden != 0L

private fun app.cash.sqldelight.db.QueryResult<Long>.requireSingleUpdate(operation: String) {
    if (value != 1L) {
        persistenceFailure("Could not $operation because the durable state changed unexpectedly.")
    }
}

private fun String.toDatabaseLocalId(): Long = toLongOrNull()
    ?: persistenceFailure("Invalid numeric local ID: $this")

private fun persistenceFailure(reason: String): Nothing =
    throw UserDataException(UserDataError.Persistence(reason))
