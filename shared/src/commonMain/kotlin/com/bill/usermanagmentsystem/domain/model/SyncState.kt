package com.bill.usermanagmentsystem.domain.model

sealed interface SyncState {
    data object Idle : SyncState

    data object Running : SyncState

    data class Failed(
        val error: UserDataError,
    ) : SyncState
}
