package com.bill.usermanagmentsystem.data.local

import com.bill.usermanagmentsystem.domain.model.AddUserInput
import com.bill.usermanagmentsystem.domain.model.UserRecord
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

internal interface UserLocalDataSource {
    fun observeVisibleUsers(): Flow<List<UserRecord>>

    suspend fun getUser(localId: String): StoredUser?

    suspend fun getAllMutations(): List<StoredMutation>

    suspend fun insertPendingCreate(
        localId: String,
        mutationId: String,
        input: AddUserInput,
        observedAt: Instant,
    )

    suspend fun requestDelete(
        localId: String,
        undoDeadline: Instant,
    )

    suspend fun undoDelete(
        localId: String,
        now: Instant,
    )

    suspend fun finalizeExpiredDeletes(now: Instant): Int

    suspend fun getDueMutations(now: Instant): List<DueMutation>

    suspend fun completeCreate(
        mutationId: String,
        localId: String,
        remoteUser: SnapshotUser,
    )

    suspend fun markCreateFailed(
        mutationId: String,
        localId: String,
        reason: String,
    )

    suspend fun retryFailedCreate(
        localId: String,
        mutationId: String,
        createdAt: Instant,
    )

    suspend fun completeDelete(
        mutationId: String,
        localId: String,
    )

    suspend fun restoreAfterPermanentDeleteFailure(
        mutationId: String,
        localId: String,
        reason: String,
    )

    suspend fun markMutationRetryable(
        mutationId: String,
        retryAt: Instant,
        reason: String,
    )

    suspend fun markMutationBlocked(
        mutationId: String,
        reason: String,
    )

    suspend fun retryBlockedMutation(mutationId: String)

    suspend fun mergeSnapshot(
        users: List<SnapshotUser>,
        observedAt: Instant,
    )
}
