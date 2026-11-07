package br.app.stagebook.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import br.app.stagebook.domain.MetronomeClick
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The metronome click on the device's regular output (headphones, speaker; doesn't go through the backing track
 * engine). A thread writes audio nonstop (silence + clicks on the right sample), so the timing doesn't drift.
 */
@Singleton
class MetronomeSound @Inject constructor(@ApplicationContext private val context: Context) {
    @Volatile private var worker: Thread? = null
    @Volatile private var quieter = false   // the track isn't media: it comes out lower
    private val _keepsApart = MutableStateFlow(true)

    /** False when, through Android, the click can't leave the USB interface: "the device" isn't a real choice then. */
    val keepsApart: StateFlow<Boolean> = _keepsApart.asStateFlow()

    fun start(bpm: Int) {
        stop()
        val spb = MetronomeClick.samplesPerBeat(bpm)
        val normal = MetronomeClick.click(accent = false)
        val accent = MetronomeClick.click(accent = true)
        worker = Thread({
            val min = AudioTrack.getMinBufferSize(MetronomeClick.RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = openTrack(min) ?: return@Thread
            // the device's own output, never the USB interface: with the interface playing through Android (the
            // "through Android" option), the default output is the interface and the click went there too. Picked again
            // when headphones come or go while it plays (audit 02/11: plugged later, they got nothing)
            val audio = context.getSystemService(AudioManager::class.java)
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
                    // accessibility audio comes out quieter than media, even at full volume
                    if (quieter) for (i in buf.indices) buf[i] = (buf[i] * ANDROID_ROUTE_GAIN).coerceIn(-32768, 32767).toShort()
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

    /**
     * The click's track. Through Android the song is media on the interface, and each Android keeps a different kind of
     * audio apart from it: the first one it really sends to the device's output is the one that stays.
     */
    private fun openTrack(min: Int): AudioTrack? {
        val kinds = if (br.app.stagebook.player.StagePlayer.viaAndroid()) ROUTE_USAGES else listOf(AudioAttributes.USAGE_MEDIA)
        kinds.forEachIndexed { i, usage ->
            val track = buildTrack(usage, min) ?: return@forEachIndexed
            quieter = usage != AudioAttributes.USAGE_MEDIA
            if (i == kinds.lastIndex) {
                if (kinds.size > 1) _keepsApart.value = false
                return track
            }
            if (routedAway(track)) { _keepsApart.value = true; return track }
            runCatching { track.stop() }
            track.release()
        }
        return null
    }

    /**
     * A silent try for the menu, with a USB interface connected. It only ever says no: with no song playing Android may
     * route differently, so a real click ([openTrack]) has the last word.
     */
    fun probeApart() {
        val outs = context.getSystemService(AudioManager::class.java)?.getDevices(AudioManager.GET_DEVICES_OUTPUTS) ?: return
        if (outs.none { it.type in USB_OUTPUTS }) return
        val min = AudioTrack.getMinBufferSize(MetronomeClick.RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val apart = ROUTE_USAGES.dropLast(1).any { usage ->
            val track = buildTrack(usage, min) ?: return@any false
            try { routedAway(track) } finally { runCatching { track.stop() }; track.release() }
        }
        if (!apart) _keepsApart.value = false
    }

    private fun buildTrack(usage: Int, min: Int): AudioTrack? = runCatching {
        AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(MetronomeClick.RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(maxOf(min, CHUNK * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }.getOrNull()?.also { track -> deviceOutput()?.let { track.setPreferredDevice(it) } }

    /** Plays silence for a moment: Android only says where a track goes once it plays. */
    private fun routedAway(track: AudioTrack): Boolean {
        track.play()
        track.write(ShortArray(CHUNK * 2), 0, CHUNK * 2)
        runCatching { Thread.sleep(ROUTE_WAIT_MS) }
        val at = track.routedDevice?.type
        android.util.Log.i("stagebook", "click routed to $at")
        return at !in USB_OUTPUTS
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
        const val ANDROID_ROUTE_GAIN = 3
        const val ROUTE_WAIT_MS = 120L
        val USB_OUTPUTS = setOf(AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET)
        // the last one is what's left when Android won't separate them: the click goes with the song, at its normal level.
        // Never an alarm: Android reopens the outputs for it and the song dropped when the click stopped
        val ROUTE_USAGES = listOf(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY, AudioAttributes.USAGE_ASSISTANCE_SONIFICATION, AudioAttributes.USAGE_MEDIA)
        val DEVICE_PREFERENCE = listOf(
            // USB-C headphones and dongles are headsets, not the interface (audit 02/11: the click went to the speaker)
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        )
    }
}
