package com.xennmap.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Emits whether the device currently has usable internet connectivity. */
@Singleton
class ConnectivityObserver @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val isOnline: Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        fun sendCurrent() {
            trySend(currentlyOnline(manager))
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = sendCurrent()
            override fun onLost(network: Network) = sendCurrent()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = sendCurrent()
        }
        sendCurrent()
        runCatching {
            manager.registerDefaultNetworkCallback(callback)
        }
        awaitClose {
            runCatching { manager.unregisterNetworkCallback(callback) }
        }
    }

    private fun currentlyOnline(manager: ConnectivityManager?): Boolean {
        manager ?: return false
        val active = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
