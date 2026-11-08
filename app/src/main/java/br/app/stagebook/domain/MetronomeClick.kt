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

    // count-in (user, 07/11): "1 . 2 . 1 2 3 4", two slow beats and a bar, so it isn't taken for the song's start
    const val COUNT_IN_BEATS = 8
    const val COUNT_IN_SKIP = 0b1010L   // beats 2 and 4 from the start stay silent (bit n = beat n)
    const val FULL_VOLUME = 100

    fun samplesPerBeat(bpm: Int, rate: Int = RATE): Long = rate * 60L / bpm.coerceAtLeast(1)

    /** The number the stage shows on [beat] of the count-in (0 = none: a pause, or the count is over). */
    fun countLabel(beat: Int): Int = when (beat) {
        0 -> 1
        2 -> 2
        in 4 until COUNT_IN_BEATS -> beat - 3
        else -> 0
    }

    /** Whether [beat] from the start is left silent ([skip] as [COUNT_IN_SKIP]). */
    fun silent(beat: Long, skip: Long): Boolean = beat < 64 && (skip shr beat.toInt()) and 1L == 1L

    /** Whether [beat] carries the accent: the first of each bar, and the count-in's second slow click, so the two slow
     *  clicks sound the same (user, 07/11: the accent, like a bar start). [skip] as [COUNT_IN_SKIP], 0 = no count-in. */
    fun accented(beat: Long, beatsPerBar: Int, skip: Long): Boolean = beat % beatsPerBar == 0L || (skip != 0L && beat == 2L)

    /** A short click: a sine with a fast decay (sounds like a metronome's "tick"). */
    fun click(accent: Boolean, rate: Int = RATE, volume: Int = FULL_VOLUME): ShortArray {
        val gain = volume.coerceIn(0, FULL_VOLUME) / FULL_VOLUME.toDouble()
        val n = rate * CLICK_MS / 1000
        val hz = if (accent) ACCENT_HZ else NORMAL_HZ
        return ShortArray(n) { i ->
            val t = i.toDouble() / rate
            (sin(2 * PI * hz * t) * exp(-t * 180) * VOLUME * gain * Short.MAX_VALUE).toInt().toShort()
        }
    }

    /** Fills [out] (silence + clicks) with the audio for samples [fromSample] to [fromSample] + out.size. */
    fun render(out: ShortArray, fromSample: Long, spb: Long, normal: ShortArray, accent: ShortArray, beatsPerBar: Int = 4, skip: Long = 0) {
        out.fill(0)
        val end = fromSample + out.size
        // a click that started before this chunk may still be sounding
        var beat = maxOf(0L, (fromSample - normal.size) / spb)
        while (beat * spb < end) {
            val start = beat * spb
            if (silent(beat, skip)) { beat++; continue }
            val sound = if (accented(beat, beatsPerBar, skip)) accent else normal
            for (i in sound.indices) {
                val at = start + i - fromSample
                if (at in out.indices) out[at.toInt()] = sound[i]
            }
            beat++
        }
    }
}
