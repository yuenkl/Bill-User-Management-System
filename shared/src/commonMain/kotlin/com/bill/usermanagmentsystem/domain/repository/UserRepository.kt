package com.bill.usermanagmentsystem.domain.repository

import com.bill.usermanagmentsystem.domain.model.AddUserInput
import com.bill.usermanagmentsystem.domain.model.DeletedUserUndo
import com.bill.usermanagmentsystem.domain.model.SyncState
import com.bill.usermanagmentsystem.domain.model.UndoableDeletion
import com.bill.usermanagmentsystem.domain.model.UserRecord
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

data class PageLoadResult(
    val loadedCount: Int,
    val hasMore: Boolean,
)

interface UserRepository {
    fun observeUsers(): Flow<List<UserRecord>>

    fun observeSyncState(): Flow<SyncState>

    fun observeUndoableDeletions(): Flow<List<UndoableDeletion>>

    suspend fun refresh(): Result<Unit>

    suspend fun loadNextPage(): Result<PageLoadResult>

    suspend fun addUser(input: AddUserInput): Result<String>

    suspend fun deleteImmediately(localId: String): Result<DeletedUserUndo>

    suspend fun restoreDeletedUser(input: AddUserInput): Result<String>

    suspend fun requestDelete(
        localId: String,
        undoDeadline: Instant,
    ): Result<Unit>

    suspend fun undoDelete(localId: String): Result<Unit>

    suspend fun finalizeExpiredDeletions(): Result<Int>

    suspend fun retryCreate(localId: String): Result<Unit>

    suspend fun retryBlockedSynchronization(): Result<Unit>

    suspend fun syncPending(): Result<Unit>
}
