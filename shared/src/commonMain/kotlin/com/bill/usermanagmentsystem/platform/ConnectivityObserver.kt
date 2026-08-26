package com.bill.usermanagmentsystem.platform

import kotlinx.coroutines.flow.StateFlow

enum class ConnectivityStatus {
    Unknown,
    Available,
    Unavailable,
}

interface ConnectivityObserver {
    val status: StateFlow<ConnectivityStatus>
}
