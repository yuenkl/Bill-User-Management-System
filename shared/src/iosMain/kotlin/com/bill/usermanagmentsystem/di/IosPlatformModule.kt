package com.bill.usermanagmentsystem.di

import com.bill.usermanagmentsystem.platform.AppLifecycleObserver
import com.bill.usermanagmentsystem.platform.ConnectivityObserver
import com.bill.usermanagmentsystem.platform.IosAppLifecycleObserver
import com.bill.usermanagmentsystem.platform.IosConnectivityObserver
import com.bill.usermanagmentsystem.platform.IosNetworkEngineFactory
import com.bill.usermanagmentsystem.platform.IosSqlDriverFactory
import com.bill.usermanagmentsystem.platform.NetworkEngineFactory
import com.bill.usermanagmentsystem.platform.SqlDriverFactory
import org.koin.core.module.Module
import org.koin.dsl.module

fun iosPlatformModule(): Module = module {
    single<NetworkEngineFactory> { IosNetworkEngineFactory() }
    single<SqlDriverFactory> { IosSqlDriverFactory() }
    single<ConnectivityObserver> { IosConnectivityObserver() }
    single<AppLifecycleObserver> { IosAppLifecycleObserver() }
}
