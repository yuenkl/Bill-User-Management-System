package com.bill.usermanagmentsystem.di

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.bill.usermanagmentsystem.data.local.db.UserManagementDatabase
import com.bill.usermanagmentsystem.data.remote.UserRemoteDataSource
import com.bill.usermanagmentsystem.data.testing.FakeUserRemoteDataSource
import com.bill.usermanagmentsystem.domain.repository.UserRepository
import com.bill.usermanagmentsystem.platform.AppConfig
import com.bill.usermanagmentsystem.platform.AppLifecycleObserver
import com.bill.usermanagmentsystem.platform.ConnectivityObserver
import com.bill.usermanagmentsystem.platform.NetworkEngineFactory
import com.bill.usermanagmentsystem.platform.SqlDriverFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidPlatformModuleTest {
    @Test
    fun platformGraphCreatesFoundationServices() {
        val androidApplication = ApplicationProvider.getApplicationContext<Application>()
        val application = koinApplication {
            modules(
                commonModule(AppConfig(apiToken = "token")),
                androidPlatformModule(androidApplication),
            )
        }

        val engine = application.koin.get<NetworkEngineFactory>().create()
        val driver = application.koin.get<SqlDriverFactory>().create(
            schema = TestSqlSchema,
            name = "foundation-platform-test.db",
        )
        try {
            assertNotNull(application.koin.get<ConnectivityObserver>())
            assertNotNull(application.koin.get<AppLifecycleObserver>())
        } finally {
            driver.close()
            engine.close()
            application.close()
            androidApplication.deleteDatabase("foundation-platform-test.db")
        }
    }

    @Test
    fun platformGraphCreatesOfflineRepositoryAndClosesItsScope() {
        val androidApplication = ApplicationProvider.getApplicationContext<Application>()
        val databaseName = "offline-data-graph-test.db"
        androidApplication.deleteDatabase(databaseName)
        val application = koinApplication {
            modules(
                commonModule(AppConfig(apiToken = "token")),
                androidPlatformModule(androidApplication),
                module {
                    single<UserRemoteDataSource> { FakeUserRemoteDataSource() }
                },
                offlineDataModule(databaseName),
            )
        }

        val applicationScope = application.koin.get<CoroutineScope>()
        try {
            assertNotNull(application.koin.get<UserManagementDatabase>())
            assertNotNull(application.koin.get<UserRepository>())
        } finally {
            application.close()
            androidApplication.deleteDatabase(databaseName)
        }

        assertTrue(applicationScope.coroutineContext[Job]?.isCancelled == true)
    }
}
