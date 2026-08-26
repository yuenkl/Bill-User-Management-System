package com.bill.usermanagmentsystem.di

import android.app.Application
import com.bill.usermanagmentsystem.platform.AppConfig

fun initKoinAndroid(
    application: Application,
    apiToken: String,
) {
    initKoin(
        appConfig = AppConfig(apiToken = apiToken),
        platformModule = androidPlatformModule(application),
    )
}
