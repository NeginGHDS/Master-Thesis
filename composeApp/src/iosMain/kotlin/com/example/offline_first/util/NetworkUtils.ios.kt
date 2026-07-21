package com.example.offline_first.util

import kotlin.concurrent.Volatile
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create

@Volatile
private var cachedOnline = false

@OptIn(ExperimentalForeignApi::class)
private val snapshotMonitor by lazy {
    val monitor = nw_path_monitor_create()
    val queue = dispatch_queue_create("offline_first.network.snapshot", null)
    nw_path_monitor_set_queue(monitor, queue)
    nw_path_monitor_set_update_handler(monitor) { path ->
        cachedOnline = nw_path_get_status(path) == nw_path_status_satisfied
    }
    nw_path_monitor_start(monitor)
    monitor
}

/** One-shot connectivity snapshot, backed by a lazily-started [platform.Network.nw_path_monitor_t]. */
@OptIn(ExperimentalForeignApi::class)
actual fun isNetworkOnline(): Boolean {
    // touch the lazily-started monitor so `cachedOnline` reflects the live path status
    snapshotMonitor
    return cachedOnline
}
