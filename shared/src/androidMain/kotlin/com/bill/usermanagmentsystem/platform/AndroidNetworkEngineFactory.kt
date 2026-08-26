package com.bill.usermanagmentsystem.platform

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

class AndroidNetworkEngineFactory : NetworkEngineFactory {
    override fun create(): HttpClientEngine = OkHttp.create()
}
