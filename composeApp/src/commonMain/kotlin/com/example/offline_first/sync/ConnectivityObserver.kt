package com.example.offline_first.sync

import kotlinx.coroutines.flow.Flow

/**
 * Observes device connectivity. Implementations must emit the current state immediately on
 * collection and again on every change (deduplicated) — this is what lets [SyncManager] react to
 * "network just came back" without polling. See platform `actual` sources for the OS-level hookup
 * (Android: `ConnectivityManager.NetworkCallback`, iOS: `NWPathMonitor`).
 */
interface ConnectivityObserver {
    val isOnline: Flow<Boolean>
}

/** Creates the platform-appropriate [ConnectivityObserver]. */
expect fun createConnectivityObserver(): ConnectivityObserver
