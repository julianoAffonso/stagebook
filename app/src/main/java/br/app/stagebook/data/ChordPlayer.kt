package br.app.stagebook.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import br.app.stagebook.data.model.ChordPlayMode
import br.app.stagebook.domain.ChordSynth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays the stage chord on the regular output (doesn't go through the backing track engine); a new tap replaces the
 * previous one.
 */
@Singleton
class ChordPlayer @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    @Volatile private var track: AudioTrack? = null

    // two quick taps: the first chord's track was kept by nobody (never released, and both played at once). Each tap
    // takes a number; only the newest one may put its track in place, under a lock (audit 30/10)
    private val lock = Any()
    private var latest = 0L

    fun play(notes: List<Int>, mode: ChordPlayMode, keyboard: Boolean) {
        val mine = synchronized(lock) { ++latest }
        job?.cancel()
        stopTrack()
        job = scope.launch {
            val pcm = ChordSynth.render(notes, mode, keyboard)
            if (pcm.isEmpty()) return@launch
            ensureActive()
            val t = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(ChordSynth.RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            }.getOrNull() ?: return@launch
            t.write(pcm, 0, pcm.size)
            synchronized(lock) {
                if (mine != latest) { t.release(); return@launch }   // a newer tap came meanwhile
                track?.let { runCatching { it.stop() }; it.release() }
                track = t
                t.play()
            }
        }
    }

    fun stop() {
        synchronized(lock) { latest++ }
        job?.cancel()
        stopTrack()
    }

    private fun stopTrack() = synchronized(lock) {
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
    }
}
