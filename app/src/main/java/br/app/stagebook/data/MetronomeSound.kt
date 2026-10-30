package br.app.stagebook.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import br.app.stagebook.domain.MetronomeClick
import javax.inject.Inject
import javax.inject.Singleton

/**
 * O clique do metrônomo pela saída normal do aparelho (fone, caixa; não passa pelo motor das backing tracks). Uma linha
 * de execução escreve o áudio sem parar (silêncio + cliques na amostra certa), então o tempo não escorrega.
 */
@Singleton
class MetronomeSound @Inject constructor() {
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

    private companion object { const val CHUNK = 960 }   // 20 ms por escrita
}
