package com.bill.usermanagmentsystem.di

import app.cash.sqldelight.db.SqlDriver
import com.bill.usermanagmentsystem.data.local.IdGenerator
import com.bill.usermanagmentsystem.data.local.SqlDelightUserLocalDataSource
import com.bill.usermanagmentsystem.data.local.UserLocalDataSource
import com.bill.usermanagmentsystem.data.local.UuidIdGenerator
import com.bill.usermanagmentsystem.data.local.db.UserManagementDatabase
import com.bill.usermanagmentsystem.data.repository.OfflineFirstUserRepository
import com.bill.usermanagmentsystem.data.sync.DefaultSyncCoordinator
import com.bill.usermanagmentsystem.data.sync.RetryPolicy
import com.bill.usermanagmentsystem.data.sync.SyncCoordinator
import com.bill.usermanagmentsystem.domain.repository.UserRepository
import com.bill.usermanagmentsystem.platform.SqlDriverFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose

private const val USER_DATABASE_NAME = "user-management.db"

internal fun offlineDataModule(
    databaseName: String = USER_DATABASE_NAME,
): Module = module {
    single<CoroutineDispatcher> { Dispatchers.Default }
    single<CoroutineScope> {
        CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>())
    } onClose { scope -> scope?.cancel() }

    single<SqlDriver> {
        get<SqlDriverFactory>().create(
            schema = UserManagementDatabase.Schema,
            name = databaseName,
        )
    } onClose { driver -> driver?.close() }
    single { UserManagementDatabase(get()) }

    single<IdGenerator> { UuidIdGenerator() }
    single<UserLocalDataSource> {
        SqlDelightUserLocalDataSource(
            database = get(),
            idGenerator = get(),
            queryDispatcher = get(),
        )
    }
    single { RetryPolicy() }
    single<SyncCoordinator> {
        DefaultSyncCoordinator(
            localDataSource = get(),
            remoteDataSource = get(),
            connectivityObserver = get(),
            timeProvider = get(),
            retryPolicy = get(),
            applicationScope = get(),
        )
    }
    single<UserRepository> {
        OfflineFirstUserRepository(
            localDataSource = get(),
            syncCoordinator = get(),
            idGenerator = get(),
            timeProvider = get(),
            applicationScope = get(),
        )
    }
}
