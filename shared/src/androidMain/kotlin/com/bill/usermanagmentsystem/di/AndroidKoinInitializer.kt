package com.bill.usermanagmentsystem.di

import android.app.Application
import com.bill.usermanagmentsystem.platform.AppConfig

fun initKoinAndroid(
    application: Application,
    apiToken: String,
    enableApiLogging: Boolean,
) {
    initKoin(
        appConfig = AppConfig(
            apiToken = apiToken,
            enableApiLogging = enableApiLogging,
        ),
        platformModule = androidPlatformModule(application),
    )
}
