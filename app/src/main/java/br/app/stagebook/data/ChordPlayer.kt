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
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Toca o acorde do palco pela saída normal (não passa pelo motor das backing tracks); um novo toque troca o anterior. */
@Singleton
class ChordPlayer @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    @Volatile private var track: AudioTrack? = null

    fun play(notes: List<Int>, mode: ChordPlayMode, keyboard: Boolean) {
        job?.cancel()
        stopTrack()
        job = scope.launch {
            val pcm = ChordSynth.render(notes, mode, keyboard)
            if (pcm.isEmpty()) return@launch
            val t = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(ChordSynth.RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            }.getOrNull() ?: return@launch
            t.write(pcm, 0, pcm.size)
            track = t
            t.play()
        }
    }

    fun stop() { job?.cancel(); stopTrack() }

    private fun stopTrack() {
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
    }
}
