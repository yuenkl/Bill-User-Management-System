package com.bill.usermanagmentsystem.data.sync

import com.bill.usermanagmentsystem.domain.model.SyncState
import com.bill.usermanagmentsystem.domain.repository.PageLoadResult
import kotlinx.coroutines.flow.StateFlow

internal interface SyncCoordinator {
    val state: StateFlow<SyncState>

    suspend fun sync(): Result<Unit>

    suspend fun loadNextPage(): Result<PageLoadResult>
}
