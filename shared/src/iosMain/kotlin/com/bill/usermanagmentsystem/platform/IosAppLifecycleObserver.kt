package com.bill.usermanagmentsystem.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState

@OptIn(ExperimentalForeignApi::class)
class IosAppLifecycleObserver : AppLifecycleObserver {
    private val mutableState = MutableStateFlow(
        if (
            UIApplication.sharedApplication.applicationState ==
            UIApplicationState.UIApplicationStateActive
        ) {
            AppLifecycleState.Foreground
        } else {
            AppLifecycleState.Background
        },
    )

    @Suppress("unused")
    private val foregroundObserver = NSNotificationCenter.defaultCenter.addObserverForName(
        name = UIApplicationDidBecomeActiveNotification,
        `object` = null,
        queue = NSOperationQueue.mainQueue,
    ) {
        mutableState.value = AppLifecycleState.Foreground
    }

    @Suppress("unused")
    private val backgroundObserver = NSNotificationCenter.defaultCenter.addObserverForName(
        name = UIApplicationDidEnterBackgroundNotification,
        `object` = null,
        queue = NSOperationQueue.mainQueue,
    ) {
        mutableState.value = AppLifecycleState.Background
    }

    override val state: StateFlow<AppLifecycleState> = mutableState.asStateFlow()
}
