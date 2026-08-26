package com.bill.usermanagmentsystem.platform

const val DEFAULT_GOREST_BASE_URL = "https://gorest.co.in/public/v2/"

data class AppConfig(
    val apiToken: String,
    val baseUrl: String = DEFAULT_GOREST_BASE_URL,
    val enableApiLogging: Boolean = false,
) {
    val configurationState: ConfigurationState
        get() = when {
            apiToken.isBlank() -> ConfigurationState.MissingApiToken
            baseUrl.isBlank() -> ConfigurationState.InvalidBaseUrl
            else -> ConfigurationState.Ready
        }
}

sealed interface ConfigurationState {
    data object Ready : ConfigurationState
    data object MissingApiToken : ConfigurationState
    data object InvalidBaseUrl : ConfigurationState
}
