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
            // "through Android" option), the default output is the interface and the click went there too
            deviceOutput()?.let { track.setPreferredDevice(it) }
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
                runCatching { track.stop() }
                track.release()
            }
        }, "metronomo").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    fun stop() {
        worker?.interrupt()
        worker = null
    }

    /** Headphones (wired or Bluetooth) when connected, otherwise the speaker. */
    private fun deviceOutput(): AudioDeviceInfo? {
        val outs = context.getSystemService(AudioManager::class.java)?.getDevices(AudioManager.GET_DEVICES_OUTPUTS) ?: return null
        return DEVICE_PREFERENCE.firstNotNullOfOrNull { type -> outs.firstOrNull { it.type == type } }
    }

    private companion object {
        const val CHUNK = 960   // 20 ms per write
        val DEVICE_PREFERENCE = listOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        )
    }
}
