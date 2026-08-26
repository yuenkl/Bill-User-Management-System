package com.bill.usermanagmentsystem.data.sync

import com.bill.usermanagmentsystem.domain.model.SyncState
import kotlinx.coroutines.flow.StateFlow

internal interface SyncCoordinator {
    val state: StateFlow<SyncState>

    suspend fun sync(): Result<Unit>
}
