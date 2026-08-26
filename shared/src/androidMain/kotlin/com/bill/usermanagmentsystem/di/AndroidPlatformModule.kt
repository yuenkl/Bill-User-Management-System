package com.bill.usermanagmentsystem.di

import android.app.Application
import com.bill.usermanagmentsystem.platform.AndroidAppLifecycleObserver
import com.bill.usermanagmentsystem.platform.AndroidConnectivityObserver
import com.bill.usermanagmentsystem.platform.AndroidNetworkEngineFactory
import com.bill.usermanagmentsystem.platform.AndroidSqlDriverFactory
import com.bill.usermanagmentsystem.platform.AppLifecycleObserver
import com.bill.usermanagmentsystem.platform.ConnectivityObserver
import com.bill.usermanagmentsystem.platform.NetworkEngineFactory
import com.bill.usermanagmentsystem.platform.SqlDriverFactory
import org.koin.core.module.Module
import org.koin.dsl.module

fun androidPlatformModule(application: Application): Module = module {
    single<NetworkEngineFactory> { AndroidNetworkEngineFactory() }
    single<SqlDriverFactory> { AndroidSqlDriverFactory(application) }
    single<ConnectivityObserver> { AndroidConnectivityObserver(application) }
    single<AppLifecycleObserver> { AndroidAppLifecycleObserver(application) }
}
