package com.bill.usermanagmentsystem.di

import app.cash.sqldelight.db.SqlDriver
import com.bill.usermanagmentsystem.data.local.SqlDelightUserLocalDataSource
import com.bill.usermanagmentsystem.data.local.UserLocalDataSource
import com.bill.usermanagmentsystem.data.local.db.UserManagementDatabase
import com.bill.usermanagmentsystem.data.repository.UserRepositoryImpl
import com.bill.usermanagmentsystem.domain.repository.UserRepository
import com.bill.usermanagmentsystem.domain.usecase.AddUser
import com.bill.usermanagmentsystem.domain.usecase.AddUserValidator
import com.bill.usermanagmentsystem.domain.usecase.DefaultDeleteUser
import com.bill.usermanagmentsystem.domain.usecase.DeleteUser
import com.bill.usermanagmentsystem.domain.usecase.LoadNextUsersPage
import com.bill.usermanagmentsystem.domain.usecase.ObserveUsers
import com.bill.usermanagmentsystem.domain.usecase.RefreshUsers
import com.bill.usermanagmentsystem.domain.usecase.UndoUserDeletion
import com.bill.usermanagmentsystem.platform.SqlDriverFactory
import com.bill.usermanagmentsystem.ui.users.UserFeedViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.koin.dsl.onClose

private const val USER_DATABASE_NAME = "user-management.db"

internal fun userFeatureModule(databaseName: String = USER_DATABASE_NAME): Module =
    module {
        single<CoroutineDispatcher> { Dispatchers.Default }

        single<SqlDriver> {
            get<SqlDriverFactory>().create(
                schema = UserManagementDatabase.Schema,
                name = databaseName,
            )
        } onClose { driver -> driver?.close() }
        single { UserManagementDatabase(get()) }

        single<UserLocalDataSource> {
            SqlDelightUserLocalDataSource(
                database = get(),
                queryDispatcher = get(),
            )
        }
        single<UserRepository> {
            UserRepositoryImpl(
                localDataSource = get(),
                remoteDataSource = get(),
                connectivityObserver = get(),
                timeProvider = get(),
            )
        }
        factory {
            val repository = get<UserRepository>()
            ObserveUsers(repository::observeUsers)
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
        factory { AddUserValidator() }
        factory<DeleteUser> {
            DefaultDeleteUser(repository = get())
        }
        factory {
            val repository = get<UserRepository>()
            UndoUserDeletion(repository::restoreDeletedUser)
        }
        viewModel {
            UserFeedViewModel(
                observeUsers = get(),
                refreshUsers = get(),
                loadNextUsersPage = get(),
                addUser = get(),
                addUserValidator = get(),
                deleteUser = get(),
                undoUserDeletion = get(),
                connectivityObserver = get(),
                lifecycleObserver = get(),
                timeProvider = get(),
                relativeTimeFormatter = get(),
                dispatcher = get(),
            )
        }
    }
