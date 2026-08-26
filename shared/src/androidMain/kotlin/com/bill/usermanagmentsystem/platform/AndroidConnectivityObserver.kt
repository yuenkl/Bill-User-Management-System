package com.bill.usermanagmentsystem.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidConnectivityObserver(context: Context) : ConnectivityObserver {
    private val connectivityManager = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    private val mutableStatus = MutableStateFlow(currentStatus())

    override val status: StateFlow<ConnectivityStatus> = mutableStatus.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            mutableStatus.value = currentStatus()
        }

        override fun onLost(network: Network) {
            mutableStatus.value = currentStatus()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            mutableStatus.value = currentStatus()
        }
    }

    init {
        connectivityManager.registerDefaultNetworkCallback(callback)
    }

    private fun currentStatus(): ConnectivityStatus {
        val network = connectivityManager.activeNetwork ?: return ConnectivityStatus.Unavailable
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: return ConnectivityStatus.Unavailable
        return if (
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        ) {
            ConnectivityStatus.Available
        } else {
            ConnectivityStatus.Unavailable
        }
    }
}
