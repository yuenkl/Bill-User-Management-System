package com.bill.usermanagmentsystem.platform

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.logging.Logger

interface NetworkEngineFactory {
    fun create(): HttpClientEngine

    val apiLogger: Logger
}
