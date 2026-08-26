package com.bill.usermanagmentsystem.domain.usecase

import com.bill.usermanagmentsystem.domain.model.AddUserInput
import com.bill.usermanagmentsystem.domain.model.SyncState
import com.bill.usermanagmentsystem.domain.model.UndoableDeletion
import com.bill.usermanagmentsystem.domain.model.UserRecord
import com.bill.usermanagmentsystem.domain.repository.UserRepository
import com.bill.usermanagmentsystem.platform.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
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
    suspend operator fun invoke(localId: String): Result<Instant>
}

class DefaultDeleteUserWithUndo(
    private val repository: UserRepository,
    private val timeProvider: TimeProvider,
    private val undoWindow: Duration = 5.seconds,
) : DeleteUserWithUndo {
    override suspend fun invoke(localId: String): Result<Instant> {
        val deadline = timeProvider.now() + undoWindow
        return repository.requestDelete(localId, deadline).map { deadline }
    }
}

fun interface UndoUserDeletion {
    suspend operator fun invoke(localId: String): Result<Unit>
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
