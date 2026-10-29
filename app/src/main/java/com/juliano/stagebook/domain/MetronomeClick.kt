package com.juliano.stagebook.domain

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * O som do metrônomo, amostra a amostra: cada clique começa na amostra exata do tempo (sem acumular atraso ao longo da
 * música). [render] preenche um pedaço do áudio a partir de [fromSample]; o primeiro tempo de cada compasso soa mais agudo.
 */
object MetronomeClick {
    const val RATE = 48_000
    private const val CLICK_MS = 30
    private const val NORMAL_HZ = 1_500.0
    private const val ACCENT_HZ = 2_200.0
    private const val VOLUME = 0.6

    fun samplesPerBeat(bpm: Int, rate: Int = RATE): Long = rate * 60L / bpm.coerceAtLeast(1)

    /** Um clique curto: seno com queda rápida (soa como o "tic" de um metrônomo). */
    fun click(accent: Boolean, rate: Int = RATE): ShortArray {
        val n = rate * CLICK_MS / 1000
        val hz = if (accent) ACCENT_HZ else NORMAL_HZ
        return ShortArray(n) { i ->
            val t = i.toDouble() / rate
            (sin(2 * PI * hz * t) * exp(-t * 180) * VOLUME * Short.MAX_VALUE).toInt().toShort()
        }
    }

    /** Preenche [out] (silêncio + cliques) com o áudio das amostras [fromSample] até [fromSample] + out.size. */
    fun render(out: ShortArray, fromSample: Long, spb: Long, normal: ShortArray, accent: ShortArray, beatsPerBar: Int = 4) {
        out.fill(0)
        val end = fromSample + out.size
        // o clique que começou antes deste pedaço ainda pode estar tocando
        var beat = maxOf(0L, (fromSample - normal.size) / spb)
        while (beat * spb < end) {
            val start = beat * spb
            val sound = if (beat % beatsPerBar == 0L) accent else normal
            for (i in sound.indices) {
                val at = start + i - fromSample
                if (at in out.indices) out[at.toInt()] = sound[i]
            }
            beat++
        }
    }
}
