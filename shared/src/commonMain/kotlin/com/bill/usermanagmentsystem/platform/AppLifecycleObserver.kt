package com.bill.usermanagmentsystem.platform

import kotlinx.coroutines.flow.StateFlow

enum class AppLifecycleState {
    Foreground,
    Background,
}

interface AppLifecycleObserver {
    val state: StateFlow<AppLifecycleState>
}
