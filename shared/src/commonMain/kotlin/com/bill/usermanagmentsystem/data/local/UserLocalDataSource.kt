package com.bill.usermanagmentsystem.data.local

import com.bill.usermanagmentsystem.domain.model.UserRecord
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

internal interface UserLocalDataSource {
    fun observeUsers(): Flow<List<UserRecord>>

    suspend fun getUser(localId: String): StoredUser?

    suspend fun deleteUser(localId: String): StoredUser

    suspend fun mergeSnapshot(
        users: List<SnapshotUser>,
        observedAt: Instant,
    )

    suspend fun mergePage(
        users: List<SnapshotUser>,
        observedAt: Instant,
    )
}
