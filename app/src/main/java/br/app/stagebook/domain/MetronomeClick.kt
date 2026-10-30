package br.app.stagebook.domain

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * The metronome sound, sample by sample: each click starts on the beat's exact sample (no delay builds up over the
 * song). [render] fills a chunk of audio from [fromSample]; the first beat of each bar sounds higher.
 */
object MetronomeClick {
    const val RATE = 48_000
    private const val CLICK_MS = 30
    private const val NORMAL_HZ = 1_500.0
    private const val ACCENT_HZ = 2_200.0
    private const val VOLUME = 0.6

    fun samplesPerBeat(bpm: Int, rate: Int = RATE): Long = rate * 60L / bpm.coerceAtLeast(1)

    /** A short click: a sine with a fast decay (sounds like a metronome's "tick"). */
    fun click(accent: Boolean, rate: Int = RATE): ShortArray {
        val n = rate * CLICK_MS / 1000
        val hz = if (accent) ACCENT_HZ else NORMAL_HZ
        return ShortArray(n) { i ->
            val t = i.toDouble() / rate
            (sin(2 * PI * hz * t) * exp(-t * 180) * VOLUME * Short.MAX_VALUE).toInt().toShort()
        }
    }

    /** Fills [out] (silence + clicks) with the audio for samples [fromSample] to [fromSample] + out.size. */
    fun render(out: ShortArray, fromSample: Long, spb: Long, normal: ShortArray, accent: ShortArray, beatsPerBar: Int = 4) {
        out.fill(0)
        val end = fromSample + out.size
        // a click that started before this chunk may still be sounding
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
