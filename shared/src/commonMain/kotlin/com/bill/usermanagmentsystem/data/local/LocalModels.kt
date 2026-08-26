package com.bill.usermanagmentsystem.data.local

import com.bill.usermanagmentsystem.domain.model.Gender
import com.bill.usermanagmentsystem.domain.model.UserStatus
import kotlin.time.Instant

internal enum class StoredUserSyncStatus {
    Synced,
    PendingCreate,
    CreateFailed,
    PendingDelete,
}

internal enum class MutationKind {
    Create,
    Delete,
}

internal enum class MutationState {
    Pending,
    RetryableWait,
    Blocked,
}

internal data class StoredUser(
    val localId: String,
    val remoteId: Long?,
    val name: String,
    val email: String,
    val gender: Gender,
    val status: UserStatus,
    val observedAt: Instant,
    val serverPosition: Long?,
    val synchronization: StoredUserSyncStatus,
    val hidden: Boolean,
    val undoDeadline: Instant?,
    val lastSyncError: String?,
)

internal data class StoredMutation(
    val mutationId: String,
    val userLocalId: String,
    val kind: MutationKind,
    val createdAt: Instant,
    val attemptCount: Long,
    val state: MutationState,
    val retryAt: Instant?,
    val lastError: String?,
)

internal data class DueMutation(
    val mutation: StoredMutation,
    val remoteId: Long?,
    val name: String,
    val email: String,
    val gender: Gender,
    val status: UserStatus,
)

internal data class SnapshotUser(
    val remoteId: Long,
    val name: String,
    val email: String,
    val gender: Gender,
    val status: UserStatus,
    val serverPosition: Long,
)
