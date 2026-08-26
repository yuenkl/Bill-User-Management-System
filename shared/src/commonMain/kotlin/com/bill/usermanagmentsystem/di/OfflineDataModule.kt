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
import com.bill.usermanagmentsystem.domain.usecase.AddUser
import com.bill.usermanagmentsystem.domain.usecase.AddUserValidator
import com.bill.usermanagmentsystem.domain.usecase.DeleteUserWithUndo
import com.bill.usermanagmentsystem.domain.usecase.DefaultDeleteUserWithUndo
import com.bill.usermanagmentsystem.domain.usecase.FinalizeExpiredDeletions
import com.bill.usermanagmentsystem.domain.usecase.LoadNextUsersPage
import com.bill.usermanagmentsystem.domain.usecase.ObserveSyncState
import com.bill.usermanagmentsystem.domain.usecase.ObserveUndoableDeletions
import com.bill.usermanagmentsystem.domain.usecase.ObserveUsers
import com.bill.usermanagmentsystem.domain.usecase.RefreshUsers
import com.bill.usermanagmentsystem.domain.usecase.RetryUserCreation
import com.bill.usermanagmentsystem.domain.usecase.SyncPendingUsers
import com.bill.usermanagmentsystem.domain.usecase.UndoUserDeletion
import com.bill.usermanagmentsystem.platform.SqlDriverFactory
import com.bill.usermanagmentsystem.ui.users.UserFeedViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
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
    factory {
        val repository = get<UserRepository>()
        ObserveUsers(repository::observeUsers)
    }
    factory {
        val repository = get<UserRepository>()
        ObserveSyncState(repository::observeSyncState)
    }
    factory {
        val repository = get<UserRepository>()
        RefreshUsers(repository::refresh)
    }
    factory {
        val repository = get<UserRepository>()
        LoadNextUsersPage(repository::loadNextPage)
    }
    factory {
        val repository = get<UserRepository>()
        AddUser(repository::addUser)
    }
    factory {
        val repository = get<UserRepository>()
        RetryUserCreation(repository::retryCreate)
    }
    factory { AddUserValidator() }
    factory {
        val repository = get<UserRepository>()
        ObserveUndoableDeletions(repository::observeUndoableDeletions)
    }
    factory<DeleteUserWithUndo> {
        DefaultDeleteUserWithUndo(repository = get(), timeProvider = get())
    }
    factory {
        val repository = get<UserRepository>()
        UndoUserDeletion(repository::undoDelete)
    }
    factory {
        val repository = get<UserRepository>()
        FinalizeExpiredDeletions(repository::finalizeExpiredDeletions)
    }
    factory {
        val repository = get<UserRepository>()
        SyncPendingUsers(repository::syncPending)
    }
    viewModel {
        UserFeedViewModel(
            observeUsers = get(),
            observeSyncState = get(),
            observeUndoableDeletions = get(),
            refreshUsers = get(),
            loadNextUsersPage = get(),
            addUser = get(),
            retryUserCreationUseCase = get(),
            addUserValidator = get(),
            deleteUserWithUndo = get(),
            undoUserDeletion = get(),
            finalizeExpiredDeletions = get(),
            connectivityObserver = get(),
            lifecycleObserver = get(),
            timeProvider = get(),
            relativeTimeFormatter = get(),
            dispatcher = get(),
        )
    }
}
