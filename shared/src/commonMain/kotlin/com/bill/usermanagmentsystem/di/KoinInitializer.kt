package com.bill.usermanagmentsystem.di

import com.bill.usermanagmentsystem.platform.AppConfig
import org.koin.core.context.startKoin
import org.koin.core.module.Module

internal fun initKoin(
    appConfig: AppConfig,
    platformModule: Module,
) {
    startKoin {
        modules(
            commonModule(appConfig),
            platformModule,
            userFeatureModule(),
        )
    }
}
