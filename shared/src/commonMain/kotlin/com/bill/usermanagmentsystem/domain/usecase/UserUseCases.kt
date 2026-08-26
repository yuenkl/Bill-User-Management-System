package com.bill.usermanagmentsystem.domain.usecase

import com.bill.usermanagmentsystem.domain.model.AddUserInput
import com.bill.usermanagmentsystem.domain.model.SyncState
import com.bill.usermanagmentsystem.domain.model.UserRecord
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

fun interface ObserveUsers {
    operator fun invoke(): Flow<List<UserRecord>>
}

fun interface ObserveSyncState {
    operator fun invoke(): Flow<SyncState>
}

fun interface RefreshUsers {
    suspend operator fun invoke(): Result<Unit>
}

fun interface AddUser {
    suspend operator fun invoke(input: AddUserInput): Result<String>
}

fun interface RequestUserDeletion {
    suspend operator fun invoke(
        localId: String,
        undoDeadline: Instant,
    ): Result<Unit>
}

fun interface UndoUserDeletion {
    suspend operator fun invoke(localId: String): Result<Unit>
}

fun interface RetryUserCreation {
    suspend operator fun invoke(localId: String): Result<Unit>
}

fun interface RetryBlockedSynchronization {
    suspend operator fun invoke(): Result<Unit>
}

fun interface SyncPendingUsers {
    suspend operator fun invoke(): Result<Unit>
}
