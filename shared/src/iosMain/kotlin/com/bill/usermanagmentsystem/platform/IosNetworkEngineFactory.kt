package com.bill.usermanagmentsystem.platform

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

class IosNetworkEngineFactory : NetworkEngineFactory {
    override fun create(): HttpClientEngine = Darwin.create()
}
