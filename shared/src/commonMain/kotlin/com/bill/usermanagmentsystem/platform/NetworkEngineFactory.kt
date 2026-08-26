package com.bill.usermanagmentsystem.platform

import io.ktor.client.engine.HttpClientEngine

interface NetworkEngineFactory {
    fun create(): HttpClientEngine
}
