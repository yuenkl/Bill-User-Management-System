package com.bill.usermanagmentsystem.domain.usecase

import com.bill.usermanagmentsystem.domain.model.AddUserInput
import com.bill.usermanagmentsystem.domain.model.DeletedUserUndo
import com.bill.usermanagmentsystem.domain.model.SyncState
import com.bill.usermanagmentsystem.domain.model.UndoableDeletion
import com.bill.usermanagmentsystem.domain.model.UserRecord
import com.bill.usermanagmentsystem.domain.repository.PageLoadResult
import com.bill.usermanagmentsystem.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

fun interface ObserveUsers {
    operator fun invoke(): Flow<List<UserRecord>>
}

fun interface ObserveSyncState {
    operator fun invoke(): Flow<SyncState>
}

fun interface ObserveUndoableDeletions {
    operator fun invoke(): Flow<List<UndoableDeletion>>
}

fun interface RefreshUsers {
    suspend operator fun invoke(): Result<Unit>
}

fun interface LoadNextUsersPage {
    suspend operator fun invoke(): Result<PageLoadResult>
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

fun interface DeleteUserWithUndo {
    suspend operator fun invoke(localId: String): Result<DeletedUserUndo>
}

class DefaultDeleteUserWithUndo(
    private val repository: UserRepository,
) : DeleteUserWithUndo {
    override suspend fun invoke(localId: String): Result<DeletedUserUndo> = repository.deleteImmediately(localId)
}

fun interface UndoUserDeletion {
    suspend operator fun invoke(input: AddUserInput): Result<String>
}

fun interface FinalizeExpiredDeletions {
    suspend operator fun invoke(): Result<Int>
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
