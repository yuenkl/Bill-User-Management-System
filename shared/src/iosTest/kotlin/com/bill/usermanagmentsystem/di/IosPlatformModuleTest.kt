package com.bill.usermanagmentsystem.di

import com.bill.usermanagmentsystem.platform.AppConfig
import com.bill.usermanagmentsystem.platform.NetworkEngineFactory
import com.bill.usermanagmentsystem.platform.SqlDriverFactory
import kotlin.test.Test
import org.koin.dsl.koinApplication

class IosPlatformModuleTest {
    @Test
    fun platformGraphCreatesFoundationFactories() {
        val application = koinApplication {
            modules(
                commonModule(AppConfig(apiToken = "token")),
                iosPlatformModule(),
            )
        }

        val engine = application.koin.get<NetworkEngineFactory>().create()
        val driver = application.koin.get<SqlDriverFactory>().create(
            schema = TestSqlSchema,
            name = "foundation-platform-test.db",
        )

        driver.close()
        engine.close()
        application.close()
    }
}
