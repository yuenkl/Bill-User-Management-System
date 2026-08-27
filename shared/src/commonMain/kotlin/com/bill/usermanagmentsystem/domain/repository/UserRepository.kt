package com.bill.usermanagmentsystem.domain.repository

import com.bill.usermanagmentsystem.domain.model.AddUserInput
import com.bill.usermanagmentsystem.domain.model.DeletedUserUndo
import com.bill.usermanagmentsystem.domain.model.UserRecord
import kotlinx.coroutines.flow.Flow

data class PageLoadResult(
    val loadedCount: Int,
    val hasMore: Boolean,
)

interface UserRepository {
    fun observeUsers(): Flow<List<UserRecord>>

    suspend fun refresh(): Result<Unit>

    suspend fun loadNextPage(): Result<PageLoadResult>

    suspend fun addUser(input: AddUserInput): Result<String>

    suspend fun deleteImmediately(localId: String): Result<DeletedUserUndo>

    suspend fun restoreDeletedUser(input: AddUserInput): Result<String>
}
