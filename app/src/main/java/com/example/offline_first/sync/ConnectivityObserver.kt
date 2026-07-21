package com.example.offline_first.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.offline_first.util.NetworkUtils
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class ConnectivityObserver(context: Context) {

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    //   ConnectivityObserver wraps Android's NetworkCallback in a callbackFlow. The OS calls the callback the instant network state changes — no polling involved. It emits the
    //  current state immediately on collection, then emits again on every change. distinctUntilChanged() prevents duplicate emissions (e.g. multiple onCapabilitiesChanged events
    //   for the same connected state).
    val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                trySend(validated)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm.registerNetworkCallback(request, callback)

        // emit the current state immediately so collectors don't wait for a change
        trySend(NetworkUtils.isOnline(context))

        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}