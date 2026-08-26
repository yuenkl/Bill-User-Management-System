package com.bill.usermanagmentsystem.platform

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidAppLifecycleObserver(application: Application) :
    AppLifecycleObserver,
    Application.ActivityLifecycleCallbacks {

    private val mutableState = MutableStateFlow(AppLifecycleState.Background)
    private var startedActivityCount = 0

    override val state: StateFlow<AppLifecycleState> = mutableState.asStateFlow()

    init {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount += 1
        if (startedActivityCount == 1) {
            mutableState.value = AppLifecycleState.Foreground
        }
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
        if (startedActivityCount == 0) {
            mutableState.value = AppLifecycleState.Background
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
