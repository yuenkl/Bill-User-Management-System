package com.bill.usermanagmentsystem.di

import com.bill.usermanagmentsystem.platform.AppConfig
import com.bill.usermanagmentsystem.platform.ConfigurationState
import com.bill.usermanagmentsystem.platform.SystemTimeProvider
import com.bill.usermanagmentsystem.platform.TimeProvider
import org.koin.core.module.Module
import org.koin.dsl.module

fun commonModule(appConfig: AppConfig): Module = module {
    single { appConfig }
    single<ConfigurationState> { appConfig.configurationState }
    single<TimeProvider> { SystemTimeProvider() }
}
