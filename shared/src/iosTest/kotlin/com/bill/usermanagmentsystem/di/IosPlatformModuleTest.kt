package com.bill.usermanagmentsystem.di

import com.bill.usermanagmentsystem.data.local.db.UserManagementDatabase
import com.bill.usermanagmentsystem.data.remote.UserRemoteDataSource
import com.bill.usermanagmentsystem.data.testing.FakeUserRemoteDataSource
import com.bill.usermanagmentsystem.domain.repository.UserRepository
import com.bill.usermanagmentsystem.platform.AppConfig
import com.bill.usermanagmentsystem.platform.NetworkEngineFactory
import com.bill.usermanagmentsystem.platform.SqlDriverFactory
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertNotNull

class IosPlatformModuleTest {
    @Test
    fun platformGraphCreatesFoundationFactories() {
        val application =
            koinApplication {
                modules(
                    commonModule(AppConfig(apiToken = "token")),
                    iosPlatformModule(),
                )
            }

        val engine = application.koin.get<NetworkEngineFactory>().create()
        val driver =
            application.koin.get<SqlDriverFactory>().create(
                schema = TestSqlSchema,
                name = "foundation-platform-test.db",
            )

        assertNotNull(application.koin.get<UserRemoteDataSource>())

        driver.close()
        engine.close()
        application.close()
    }

    @Test
    fun platformGraphCreatesOfflineRepository() {
        val application =
            koinApplication {
                modules(
                    commonModule(AppConfig(apiToken = "token")),
                    iosPlatformModule(),
                    module {
                        single<UserRemoteDataSource> { FakeUserRemoteDataSource() }
                    },
                    userFeatureModule("user-data-graph-test.db"),
                )
            }

        try {
            assertNotNull(application.koin.get<UserManagementDatabase>())
            assertNotNull(application.koin.get<UserRepository>())
        } finally {
            application.close()
        }
    }
}
