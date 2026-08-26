package com.bill.usermanagmentsystem.platform

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.logging.Logger

class IosNetworkEngineFactory : NetworkEngineFactory {
    override fun create(): HttpClientEngine = Darwin.create()

    override val apiLogger: Logger = object : Logger {
        override fun log(message: String) {
            println("GoRestApi: $message")
        }
    }
}
