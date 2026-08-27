package com.bill.usermanagmentsystem.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create

@OptIn(ExperimentalForeignApi::class)
class IosConnectivityObserver : ConnectivityObserver {
    private val mutableStatus = MutableStateFlow(ConnectivityStatus.Unknown)
    private val monitor = nw_path_monitor_create()
    private val queue = dispatch_queue_create("UserManagementConnectivity", null)

    override val status: StateFlow<ConnectivityStatus> = mutableStatus.asStateFlow()

    init {
        nw_path_monitor_set_update_handler(monitor) { path ->
            mutableStatus.value =
                if (nw_path_get_status(path) == nw_path_status_satisfied) {
                    ConnectivityStatus.Available
                } else {
                    ConnectivityStatus.Unavailable
                }
        }
        nw_path_monitor_set_queue(monitor, queue)
        nw_path_monitor_start(monitor)
    }
}
