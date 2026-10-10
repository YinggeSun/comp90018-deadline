package com.comp90018.deadline.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network

/**
 * Calls [onAvailable] whenever the device gains a usable network, including once at
 * [start] if it is already online. Used to sync progress after reconnecting.
 */
class NetworkWatcher(
    context: Context,
) {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    fun start(onAvailable: () -> Unit) {
        connectivity.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = onAvailable()
            },
        )
    }
}
