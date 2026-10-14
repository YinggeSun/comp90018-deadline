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

    /** The caller owns this registration; close it when its observing lifetime ends. */
    fun start(onAvailable: () -> Unit): AutoCloseable {
        val lock = Any()
        var closed = false
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    synchronized(lock) { if (!closed) onAvailable() }
                }
            }
        connectivity.registerDefaultNetworkCallback(callback)
        return AutoCloseable {
            synchronized(lock) {
                if (!closed) {
                    closed = true
                    connectivity.unregisterNetworkCallback(callback)
                }
            }
        }
    }
}
