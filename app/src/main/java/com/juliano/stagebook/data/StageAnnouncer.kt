package com.juliano.stagebook.data

import com.juliano.stagebook.domain.StageAnnouncement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manda a música da vez na rede local para telão/transmissão ([StageAnnouncement]): na hora em que ela muda e depois a
 * cada 10 s, enquanto o palco está aberto. Um pacote pequeno por vez (custo desprezível). Só com rede local ligada (Wi-Fi,
 * cabo ou o ponto de acesso do celular, pedido do usuário, 29/10): sem ela, nem tenta.
 */
@Singleton
class StageAnnouncer @Inject constructor(private val link: BandLink) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun announce(a: StageAnnouncement) {
        job?.cancel()
        job = scope.launch {
            val bytes = a.encode().toByteArray()
            while (isActive) {
                if (link.onWifi()) runCatching {
                    DatagramSocket().use { s ->
                        s.broadcast = true
                        s.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName("255.255.255.255"), StageAnnouncement.PORT))
                    }
                }
                delay(StageAnnouncement.REPEAT_MS)
            }
        }
    }

    fun stop() { job?.cancel(); job = null }
}
