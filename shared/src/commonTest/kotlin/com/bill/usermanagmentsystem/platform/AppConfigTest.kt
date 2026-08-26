package com.bill.usermanagmentsystem.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class AppConfigTest {
    @Test
    fun blankTokenIsReportedWithoutThrowing() {
        val config = AppConfig(apiToken = "   ")

        assertEquals(ConfigurationState.MissingApiToken, config.configurationState)
    }

    @Test
    fun blankBaseUrlIsReportedWithoutThrowing() {
        val config = AppConfig(apiToken = "token", baseUrl = "")

        assertEquals(ConfigurationState.InvalidBaseUrl, config.configurationState)
    }

    @Test
    fun nonBlankConfigurationIsReady() {
        val config = AppConfig(apiToken = "token")

        assertEquals(ConfigurationState.Ready, config.configurationState)
    }
}
