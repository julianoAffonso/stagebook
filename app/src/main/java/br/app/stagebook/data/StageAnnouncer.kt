package br.app.stagebook.data

import br.app.stagebook.domain.StageAnnouncement
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
 * Sends Now Playing (the current song) on the local network to live and projection apps ([StageAnnouncement]): when it
 * changes and then every 10 s, while the stage is open. One small packet at a time (negligible cost). Only with a local
 * network on (Wi-Fi, cable or the phone's hotspot, user's request, 29/10): without it, it doesn't even try.
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
