package com.bill.usermanagmentsystem.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.bill.usermanagmentsystem.data.local.db.UserManagementDatabase
import com.bill.usermanagmentsystem.data.local.db.Users
import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.User
import com.bill.usermanagmentsystem.domain.model.UserDataError
import com.bill.usermanagmentsystem.domain.model.UserDataException
import com.bill.usermanagmentsystem.domain.model.UserRecord
import com.bill.usermanagmentsystem.domain.model.UserStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Instant

internal class SqlDelightUserLocalDataSource(
    database: UserManagementDatabase,
    private val queryDispatcher: CoroutineDispatcher,
) : UserLocalDataSource {
    private val queries = database.userManagementDatabaseQueries

    override fun observeUsers(): Flow<List<UserRecord>> =
        queries
            .selectUsers()
            .asFlow()
            .mapToList(queryDispatcher)
            .map { rows -> rows.map(Users::toDomainRecord) }
            .flowOn(queryDispatcher)

    override suspend fun getUser(localId: String): StoredUser? =
        withContext(queryDispatcher) {
            queries
                .selectUserByLocalId(localId.toDatabaseLocalId())
                .executeAsOneOrNull()
                ?.toStoredUser()
        }

    override suspend fun deleteUser(localId: String): StoredUser =
        withContext(queryDispatcher) {
            val databaseLocalId = localId.toDatabaseLocalId()
            val user =
                queries.selectUserByLocalId(databaseLocalId).executeAsOneOrNull()
                    ?: throw UserDataException(UserDataError.UserNotFound(localId))
            queries.deleteUser(databaseLocalId)
            user.toStoredUser()
        }

    override suspend fun mergeSnapshot(
        users: List<SnapshotUser>,
        observedAt: Instant,
    ) = withContext(queryDispatcher) {
        queries.transaction {
            val snapshotRemoteIds = users.mapTo(mutableSetOf(), SnapshotUser::remoteId)
            mergeUsers(users, observedAt)
            queries
                .selectRemoteUsers()
                .executeAsList()
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
                server_position = user.serverPosition,
                remote_id = user.remoteId,
            )
        }
    }
}

private fun Users.toStoredUser(): StoredUser =
    StoredUser(
        localId = local_id.toString(),
        remoteId = remote_id,
        name = name,
        email = email,
        gender = gender.toGender(),
        status = status.toUserStatus(),
        observedAt = Instant.fromEpochMilliseconds(observed_at_epoch_ms),
        serverPosition = server_position,
    )

private fun Users.toDomainRecord(): UserRecord =
    UserRecord(
        user =
            User(
                localId = local_id.toString(),
                remoteId = remote_id,
                name = name,
                email = email,
                gender = gender.toGender(),
                status = status.toUserStatus(),
                observedAt = Instant.fromEpochMilliseconds(observed_at_epoch_ms),
            ),
    )

private fun String.toGender(): Gender =
    Gender.entries.firstOrNull { it.apiValue == this }
        ?: persistenceFailure("Unknown gender: $this")

private fun String.toUserStatus(): UserStatus =
    UserStatus.entries.firstOrNull { it.apiValue == this }
        ?: persistenceFailure("Unknown user status: $this")

private fun String.toDatabaseLocalId(): Long = toLongOrNull() ?: persistenceFailure("Invalid numeric local ID: $this")

private fun persistenceFailure(reason: String): Nothing = throw UserDataException(UserDataError.Persistence(reason))
