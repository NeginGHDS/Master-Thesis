package com.example.offline_first.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.offline_first.androidAppContext

actual fun isNetworkOnline(): Boolean = isOnline(androidAppContext)

internal fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
