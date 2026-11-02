package br.app.stagebook.data

import br.app.stagebook.domain.StageAnnouncement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val scope = backgroundScope(Dispatchers.IO)   // errors logged, never a crash on stage
    private var job: Job? = null

    fun announce(a: StageAnnouncement) {
        job?.cancel()
        job = scope.launch {
            val bytes = a.encode().toByteArray()
            while (isActive) {
                if (link.onWifi()) runCatching {
                    DatagramSocket().use { s ->
                        s.broadcast = true
                        // each local network's broadcast, like the band beacon: 255.255.255.255 left only by the main
                        // network, so OBS on a phone's hotspot got nothing while the phone also had 4G (user, 02/11)
                        val targets = link.broadcastTargets().ifEmpty { listOf(InetAddress.getByName("255.255.255.255")) }
                        for (t in targets) s.send(DatagramPacket(bytes, bytes.size, t, StageAnnouncement.PORT))
                    }
                }
                delay(StageAnnouncement.REPEAT_MS)
            }
        }
    }

    fun stop() { job?.cancel(); job = null }
}
