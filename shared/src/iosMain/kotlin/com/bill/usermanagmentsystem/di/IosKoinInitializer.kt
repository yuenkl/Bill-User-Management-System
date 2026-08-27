package com.bill.usermanagmentsystem.di

import com.bill.usermanagmentsystem.platform.AppConfig

fun startKoinIos(
    apiToken: String,
    enableApiLogging: Boolean,
) {
    initKoin(
        appConfig =
            AppConfig(
                apiToken = apiToken,
                enableApiLogging = enableApiLogging,
            ),
        platformModule = iosPlatformModule(),
    )
}
