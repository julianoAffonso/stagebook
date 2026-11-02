package br.app.stagebook.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Internet now? Only watched while someone collects (the screen showing the import button): nothing listens to the
 * network with the screen closed (it only costs while in use).
 */
@Singleton
class Connectivity @Inject constructor(@ApplicationContext private val context: Context) {
    /** Internet now (a single read, without watching). */
    fun isOnline(): Boolean = isOnline(context)

    val online: Flow<Boolean> = watch { caps ->
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * On a local network, internet or not: what the band uses (device test B-26). The same rule as the band
     * ([onLocalNetwork]): the phone sharing its hotspot at the bar counts too (audit 02/11: "Sync with the band" was off
     * there while the band worked).
     */
    val localNet: Flow<Boolean> = watch { caps ->
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) || hotspotUp()
    }

    companion object {
        /** Internet now, for a screen with no view model at hand (Settings → Help). */
        fun isOnline(context: Context): Boolean {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
            return cm.getNetworkCapabilities(cm.activeNetwork)?.let {
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } == true
        }

        /**
         * Is there a local network? On Wi-Fi (or cable), or **running a hotspot**: the phone sharing internet at the bar
         * gets internet over 4G, but the others' network goes through one of its interfaces (wlan/ap/swlan) with a
         * local address. One rule for the band and the screens.
         */
        fun onLocalNetwork(context: Context): Boolean {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
            if (caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) return true
            return hotspotUp()
        }

        private val HOTSPOT = listOf("wlan", "ap", "swlan", "softap")   // hotspot interface names on Android devices

        fun hotspotUp(): Boolean = runCatching {
            java.net.NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().any { nif ->
                nif.isUp && !nif.isLoopback && HOTSPOT.any { nif.name.startsWith(it) } &&
                    nif.inetAddresses.toList().any { it is java.net.Inet4Address && it.isSiteLocalAddress }
            }
        }.getOrDefault(false)
    }

    private fun watch(read: (NetworkCapabilities) -> Boolean): Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        if (cm == null) { trySend(true); awaitClose { }; return@callbackFlow }
        fun now() = cm.getNetworkCapabilities(cm.activeNetwork)?.let(read) == true
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(now()) }
            override fun onLost(network: Network) { trySend(now()) }
            override fun onAvailable(network: Network) { trySend(now()) }
        }
        trySend(now())
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
