package com.juliano.stagebook.data

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
 * Tem internet agora? Só observa enquanto alguém coleta (a tela que mostra o botão de importar): nada fica
 * escutando a rede com a tela fechada (só gasta quando está em uso).
 */
@Singleton
class Connectivity @Inject constructor(@ApplicationContext private val context: Context) {
    val online: Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        if (cm == null) { trySend(true); awaitClose { }; return@callbackFlow }
        fun now() = cm.getNetworkCapabilities(cm.activeNetwork)?.let {
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } == true
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
