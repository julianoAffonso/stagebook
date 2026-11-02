package br.app.stagebook.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import br.app.stagebook.domain.MetronomeClick
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The metronome click on the device's regular output (headphones, speaker; doesn't go through the backing track
 * engine). A thread writes audio nonstop (silence + clicks on the right sample), so the timing doesn't drift.
 */
@Singleton
class MetronomeSound @Inject constructor(@ApplicationContext private val context: Context) {
    @Volatile private var worker: Thread? = null

    fun start(bpm: Int) {
        stop()
        val spb = MetronomeClick.samplesPerBeat(bpm)
        val normal = MetronomeClick.click(accent = false)
        val accent = MetronomeClick.click(accent = true)
        worker = Thread({
            val min = AudioTrack.getMinBufferSize(MetronomeClick.RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(MetronomeClick.RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(maxOf(min, CHUNK * 2))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            }.getOrNull() ?: return@Thread
            // the device's own output, never the USB interface: with the interface playing through Android (the
            // "through Android" option), the default output is the interface and the click went there too. Picked again
            // when headphones come or go while it plays (audit 02/11: plugged later, they got nothing)
            val audio = context.getSystemService(AudioManager::class.java)
            deviceOutput()?.let { track.setPreferredDevice(it) }
            val follow = object : android.media.AudioDeviceCallback() {
                override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) { deviceOutput()?.let { track.setPreferredDevice(it) } }
                override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) { deviceOutput()?.let { track.setPreferredDevice(it) } }
            }
            audio?.registerAudioDeviceCallback(follow, null)
            val buf = ShortArray(CHUNK)
            var pos = 0L
            try {
                track.play()
                while (!Thread.currentThread().isInterrupted) {
                    MetronomeClick.render(buf, pos, spb, normal, accent)
                    if (track.write(buf, 0, buf.size) < 0) break
                    pos += buf.size
                }
            } finally {
                audio?.unregisterAudioDeviceCallback(follow)
                runCatching { track.stop() }
                track.release()
            }
        }, "metronomo").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    fun stop() {
        worker?.interrupt()
        worker = null
    }

    /** Headphones (wired, USB-C or Bluetooth) when connected, otherwise the speaker. */
    private fun deviceOutput(): AudioDeviceInfo? {
        val all = context.getSystemService(AudioManager::class.java)?.getDevices(AudioManager.GET_DEVICES_OUTPUTS) ?: return null
        // some interfaces call themselves a USB headset: never the one the player is using
        val iface = br.app.stagebook.player.StagePlayer.interfaceName()
        val outs = all.filterNot { iface.isNotEmpty() && it.productName?.toString()?.contains(iface) == true }
        return DEVICE_PREFERENCE.firstNotNullOfOrNull { type -> outs.firstOrNull { it.type == type } }
    }

    private companion object {
        const val CHUNK = 960   // 20 ms per write
        val DEVICE_PREFERENCE = listOf(
            // USB-C headphones and dongles are headsets, not the interface (audit 02/11: the click went to the speaker)
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        )
    }
}
