package com.bill.usermanagmentsystem.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal data class AppDispatchers(
    val io: CoroutineDispatcher = platformIoDispatcher,
    val default: CoroutineDispatcher = Dispatchers.Default,
)

internal expect val platformIoDispatcher: CoroutineDispatcher
