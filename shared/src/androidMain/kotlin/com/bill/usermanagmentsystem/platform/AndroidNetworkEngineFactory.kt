package com.bill.usermanagmentsystem.platform

import android.util.Log
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.logging.Logger

class AndroidNetworkEngineFactory : NetworkEngineFactory {
    override fun create(): HttpClientEngine = OkHttp.create()

    override val apiLogger: Logger = object : Logger {
        override fun log(message: String) {
            Log.d("GoRestApi", message)
        }
    }
}
