package app.manzil.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * What Android reports about the default network. This says nothing about traffic, transit or
 * whether Manzil's weather or map data is current.
 */
enum class NetworkStatus {
    /** No default network. */
    OFFLINE,

    /** A network exists but Android has not validated internet access (captive portal, no data). */
    NO_INTERNET,

    /** Android validated internet access on the default network. */
    ONLINE,
}

private fun statusOf(caps: NetworkCapabilities?): NetworkStatus = when {
    caps == null -> NetworkStatus.OFFLINE
    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> NetworkStatus.ONLINE
    else -> NetworkStatus.NO_INTERNET
}

fun currentNetworkStatus(context: Context): NetworkStatus {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return NetworkStatus.OFFLINE
    val network = cm.activeNetwork ?: return NetworkStatus.OFFLINE
    return statusOf(cm.getNetworkCapabilities(network))
}

fun networkStatusFlow(context: Context): Flow<NetworkStatus> = callbackFlow {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    if (cm == null) {
        trySend(NetworkStatus.OFFLINE)
        awaitClose { }
        return@callbackFlow
    }
    val callback = object : ConnectivityManager.NetworkCallback() {
        private var latest: Network? = null
        override fun onAvailable(network: Network) {
            latest = network
            trySend(NetworkStatus.NO_INTERNET)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            latest = network
            trySend(statusOf(networkCapabilities))
        }

        override fun onLost(network: Network) {
            // Synchronous queries here can still return the network being lost.
            if (latest == null || latest == network) {
                latest = null
                trySend(NetworkStatus.OFFLINE)
            }
        }

        override fun onUnavailable() {
            trySend(NetworkStatus.OFFLINE)
        }
    }
    trySend(currentNetworkStatus(context))
    cm.registerDefaultNetworkCallback(callback)
    awaitClose { cm.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()
