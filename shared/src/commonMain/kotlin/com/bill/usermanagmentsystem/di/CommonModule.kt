package com.bill.usermanagmentsystem.di

import com.bill.usermanagmentsystem.data.remote.GoRestUserRemoteDataSource
import com.bill.usermanagmentsystem.data.remote.UserRemoteDataSource
import com.bill.usermanagmentsystem.data.remote.createGoRestHttpClient
import com.bill.usermanagmentsystem.domain.usecase.RelativeTimeFormatter
import com.bill.usermanagmentsystem.platform.AppConfig
import com.bill.usermanagmentsystem.platform.AppDispatchers
import com.bill.usermanagmentsystem.platform.ConfigurationState
import com.bill.usermanagmentsystem.platform.NetworkEngineFactory
import com.bill.usermanagmentsystem.platform.SystemTimeProvider
import com.bill.usermanagmentsystem.platform.TimeProvider
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose

fun commonModule(appConfig: AppConfig): Module =
    module {
        single { appConfig }
        single { AppDispatchers() }
        single<ConfigurationState> { appConfig.configurationState }
        single<TimeProvider> { SystemTimeProvider() }
        single<HttpClient> {
            createGoRestHttpClient(
                engineFactory = get<NetworkEngineFactory>(),
                enableApiLogging = appConfig.enableApiLogging,
            )
        } onClose { client -> client?.close() }
        single<UserRemoteDataSource> {
            GoRestUserRemoteDataSource(
                httpClient = get(),
                appConfig = get(),
                timeProvider = get(),
                networkDispatcher = get<AppDispatchers>().io,
            )
        }
        single { RelativeTimeFormatter() }
    }
