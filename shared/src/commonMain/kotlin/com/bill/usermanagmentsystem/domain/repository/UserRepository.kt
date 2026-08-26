package com.bill.usermanagmentsystem.domain.repository

import com.bill.usermanagmentsystem.domain.model.AddUserInput
import com.bill.usermanagmentsystem.domain.model.SyncState
import com.bill.usermanagmentsystem.domain.model.UserRecord
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface UserRepository {
    fun observeUsers(): Flow<List<UserRecord>>

    fun observeSyncState(): Flow<SyncState>

    suspend fun refresh(): Result<Unit>

    suspend fun addUser(input: AddUserInput): Result<String>

    suspend fun requestDelete(
        localId: String,
        undoDeadline: Instant,
    ): Result<Unit>

    suspend fun undoDelete(localId: String): Result<Unit>

    suspend fun retryCreate(localId: String): Result<Unit>

    suspend fun retryBlockedSynchronization(): Result<Unit>

    suspend fun syncPending(): Result<Unit>
}
