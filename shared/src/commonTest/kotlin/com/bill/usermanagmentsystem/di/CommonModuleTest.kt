package com.bill.usermanagmentsystem.di

import com.bill.usermanagmentsystem.platform.AppConfig
import com.bill.usermanagmentsystem.platform.ConfigurationState
import com.bill.usermanagmentsystem.platform.TimeProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.koin.dsl.koinApplication

class CommonModuleTest {
    @Test
    fun commonGraphResolvesConfigurationAndClock() {
        val application = koinApplication {
            modules(commonModule(AppConfig(apiToken = "token")))
        }

        assertEquals(ConfigurationState.Ready, application.koin.get<ConfigurationState>())
        assertNotNull(application.koin.get<TimeProvider>().now())

        application.close()
    }
}
