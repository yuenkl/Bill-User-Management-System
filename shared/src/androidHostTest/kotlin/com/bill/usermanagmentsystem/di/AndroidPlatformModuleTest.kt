package com.bill.usermanagmentsystem.di

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.bill.usermanagmentsystem.platform.AppConfig
import com.bill.usermanagmentsystem.platform.AppLifecycleObserver
import com.bill.usermanagmentsystem.platform.ConnectivityObserver
import com.bill.usermanagmentsystem.platform.NetworkEngineFactory
import com.bill.usermanagmentsystem.platform.SqlDriverFactory
import kotlin.test.Test
import kotlin.test.assertNotNull
import org.koin.dsl.koinApplication
import org.junit.runner.RunWith
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
}
