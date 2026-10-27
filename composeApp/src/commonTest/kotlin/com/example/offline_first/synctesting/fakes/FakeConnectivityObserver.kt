package com.example.offline_first.synctesting.fakes

import com.example.offline_first.sync.ConnectivityObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A [ConnectivityObserver] the test drives by hand, standing in for
 * `ConnectivityManager.NetworkCallback` (Android) / `NWPathMonitor` (iOS).
 *
 * Honours the interface contract the real implementations do: emits the current state immediately
 * on collection, then on every change, deduplicated.
 */
class FakeConnectivityObserver(initiallyOnline: Boolean) : ConnectivityObserver {

    private val state = MutableStateFlow(initiallyOnline)

    // StateFlow already conflates repeat values, which is exactly the deduplication the real
    // observers get from their explicit distinctUntilChanged().
    override val isOnline: Flow<Boolean> = state.asStateFlow()

    fun goOnline() {
        state.value = true
    }

    fun goOffline() {
        state.value = false
    }
}
