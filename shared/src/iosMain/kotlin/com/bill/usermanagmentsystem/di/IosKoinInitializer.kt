package com.bill.usermanagmentsystem.di

import com.bill.usermanagmentsystem.platform.AppConfig

fun startKoinIos(apiToken: String) {
    initKoin(
        appConfig = AppConfig(apiToken = apiToken),
        platformModule = iosPlatformModule(),
    )
}
