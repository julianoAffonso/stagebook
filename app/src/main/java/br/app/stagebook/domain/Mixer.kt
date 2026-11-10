package br.app.stagebook.domain

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/** Normalizing a channel (or pair): leave it, perceived loudness (LUFS) or peak. */
@Serializable
enum class NormMode { NONE, VOLUME, PEAK }

/**
 * A mixer channel. On channels 1, 3, 5 and 7, [stereo] joins the channel with the next one as a pair (the next follows
 * this one).
 */
@Immutable
@Serializable
data class MixChannel(
    val name: String = "",
    val gainDb: Float = 0f,
    val on: Boolean = true,
    val norm: NormMode = NormMode.NONE,
    val eq: Boolean = false,
    val center: Boolean = false,   // splits equally to both sides, ignoring the side it was recorded on
    val stereo: Boolean = false,
)

/**
 * An audio output: the device's or a USB interface ([key] = "usb:vid:pid"), with its name and how many outputs it has.
 */
@Immutable
@Serializable
data class OutputInfo(val key: String = DEVICE, val name: String = "", val count: Int = 2) {
    /** The outputs the engine can reach (a desk with more than [MAX] shows only the first ones). */
    val usable: Int get() = count.coerceAtMost(MAX)

    /** A chosen USB output this way doesn't have (through Android the system gives at most 8): it stays chosen, silent. */
    fun lacks(out: Int, pair: Boolean): Boolean = key != DEVICE && count > 0 && out + (if (pair) 1 else 0) >= usable

    /** A plugged interface whose outputs haven't been read yet (they come when a song with a track opens). */
    val unread: Boolean get() = key != DEVICE && count <= 0

    companion object {
        const val DEVICE = "device"
        const val MAX = 32   // engine.c OUTS_MAX: 18- and 32-channel desks, not just 8
    }
}

/**
 * The app's mixer (applies to every backing track): one channel per channel in the file. With no setup, each channel
 * goes to the output with the same number, with no gain, no normalization and no EQ: nothing is guessed.
 */
@Immutable
@Serializable
data class MixerConfig(
    val channels: Int = 2,
    val strips: List<MixChannel> = List(MAX) { MixChannel() },
    val eqDb: List<Float> = List(EQ_BANDS) { 0f },
    // the output in use (or the last used): the one Settings shows and edits
    val output: OutputInfo = OutputInfo(),
    // routing saved per output (OutputInfo key): each channel's first output; -1 = the one with the same number
    val routes: Map<String, List<Int>> = emptyMap(),
    // every interface the player has opened (key → name and outputs): plugged in again, it comes back as it was
    val known: Map<String, OutputInfo> = emptyMap(),
) {
    /**
     * What the mixer shows for the interface on the cable (user, 10/11: it kept the previous one until play): [key] null =
     * none, the device. A known one comes back with its outputs; a new one has its name and outputs still unread.
     */
    fun plugged(key: String?, name: String): OutputInfo = when {
        key == null -> OutputInfo()
        else -> known[key]?.let { k -> if (name.isEmpty()) k else k.copy(name = name) } ?: OutputInfo(key, name, 0)
    }

    /** The player opened [out]: shown, and remembered when it's an interface. */
    fun connected(out: OutputInfo): MixerConfig =
        copy(output = out, known = if (out.key == OutputInfo.DEVICE || out.count <= 0) known else known + (out.key to out))

    fun strip(i: Int): MixChannel = strips.getOrElse(i) { MixChannel() }

    /** The EQ has no switch of its own: it's on while a channel goes through it (user, 06/11). */
    val eqOn: Boolean get() = groups().any { strip(it.first).eq }

    /**
     * The EQ gains as the engine reads them: exactly [EQ_BANDS] values, missing ones flat (a shorter list, from an old
     * or hand-edited file, made the native call throw; audit 01/11).
     */
    fun eqGains(): FloatArray = FloatArray(EQ_BANDS) { eqDb.getOrElse(it) { 0f } }

    /** First output (0 = output 1) of channel [i] on output [key]; with no choice, the one with the same number. */
    fun outputOf(i: Int, key: String = output.key): Int = routes[key]?.getOrNull(i)?.takeIf { it >= 0 } ?: i

    /** Picks channel [i]'s output on the output in use; the other interfaces stay as they are. */
    fun withOutput(i: Int, out: Int): MixerConfig {
        val now = routes[output.key].orEmpty()
        return copy(routes = routes + (output.key to List(MAX) { if (it == i) out else now.getOrElse(it) { -1 } }))
    }

    fun withStrip(i: Int, change: (MixChannel) -> MixChannel): MixerConfig =
        copy(strips = List(MAX) { if (it == i) change(strip(it)) else strip(it) })

    /** Mixer channels: each alone or a pair (first, last) when the first is stereo. */
    fun groups(lanes: Int = channels): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var i = 0
        while (i < lanes.coerceIn(0, MAX)) {
            val pair = i % 2 == 0 && i + 1 < lanes && strip(i).stereo
            out += if (pair) i..i + 1 else i..i
            i += if (pair) 2 else 1
        }
        return out
    }

    companion object {
        const val MAX = 8
        const val EQ_BANDS = 5
        val EQ_FREQ = listOf(80, 250, 1000, 3500, 10000)
        const val EQ_RANGE_DB = 12f
        const val MIN_DB = -60f   // fader at the bottom = muted
        const val MAX_DB = 6f
    }
}

/**
 * The mixer's live meters: each channel in the file (with ballistics already) and the spectrum of the channels with EQ.
 */
@Immutable
data class MixerLevels(val meters: Meters, val spectrumDb: List<Float>, val live: Boolean) {
    companion object {
        val SILENT = MixerLevels(Meters.silent(MixerConfig.MAX), List(Spectrum.BINS) { Spectrum.FLOOR_DB }, live = false)
    }
}

/** Measurement of a channel or pair in a backing track: loudness (LUFS) and peak (linear, 1 = 0 dBFS). */
data class Measure(val lufs: Float, val peak: Float)

/**
 * What the engine gets: gain and normalization for each channel in the file, EQ channels and the [output][channel]
 * matrix.
 */
class MixPlan(val gains: FloatArray, val norms: FloatArray, val eqMask: Int, val route: FloatArray)

object Mixer {
    const val TARGET_LUFS = -16f        // loudness: even across songs
    const val TARGET_PEAK_DB = -6f      // peak: even across songs (click, guide)
    const val CEILING_DB = -1f          // loudness never goes over -1 dBFS peak
    const val MAX_BOOST_DB = 15f
    const val MAX_CUT_DB = -24f
    private val HALF = sqrt(0.5f)       // mono channel on both sides: same perceived loudness

    fun db(lin: Float): Float = (20 * log10(lin.coerceAtLeast(1e-9f).toDouble())).toFloat()
    fun lin(db: Float): Float = 10f.pow(db / 20f)

    /** Normalization gain (dB); with no measurement yet, none. */
    fun normDb(mode: NormMode, m: Measure?): Float {
        if (m == null || mode == NormMode.NONE) return 0f
        val g = when (mode) {
            NormMode.VOLUME -> minOf(TARGET_LUFS - m.lufs, CEILING_DB - db(m.peak))
            else -> if (m.peak > 1e-6f) TARGET_PEAK_DB - db(m.peak) else 0f
        }
        return g.coerceIn(MAX_CUT_DB, MAX_BOOST_DB)
    }

    /** Bits of the file channels for a mixer channel (to measure the pair together). */
    fun mask(group: IntRange): Int = group.fold(0) { m, c -> m or (1 shl c) }

    /** The mix for a file with [lanes] channels; [measures] per mixer channel (key: the first channel). */
    fun plan(cfg: MixerConfig, lanes: Int, measures: Map<Int, Measure> = emptyMap(), key: String = cfg.output.key): MixPlan {
        val n = MixerConfig.MAX
        val gains = FloatArray(n) { 1f }
        val norms = FloatArray(n) { 1f }
        val outs = OutputInfo.MAX
        val route = FloatArray(outs * n)   // [output * n + channel], as engine.c reads it
        var eq = 0
        for (g in cfg.groups(lanes)) {
            val s = cfg.strip(g.first)
            val gain = if (!s.on || s.gainDb <= MixerConfig.MIN_DB) 0f else lin(s.gainDb)
            val norm = lin(normDb(s.norm, measures[g.first]))
            val base = cfg.outputOf(g.first, key).coerceIn(0, outs - 1)
            for ((k, c) in g.withIndex()) {
                gains[c] = gain
                norms[c] = norm
                if (s.eq) eq = eq or (1 shl c)
                if (s.center) {
                    // pair: both sides summed in the middle; mono: the channel and the neighboring output of the same
                    // output pair
                    val left = if (g.count() == 2) base else base - base % 2
                    val w = if (g.count() == 2) 0.5f else HALF
                    for (o in left..minOf(left + 1, outs - 1)) route[o * n + c] = w
                } else {
                    val o = base + k
                    if (o < outs) route[o * n + c] = 1f
                }
            }
        }
        return MixPlan(gains, norms, eq, route)
    }
}

/**
 * A song's own mix: what was changed in the mixer over the stage stays with the song (user, 31/10). It starts from the
 * general mixer (Settings → Player) the first time it's touched; outputs, routing and the channel count stay general.
 */
@Serializable
data class SongMix(val strips: List<MixChannel> = emptyList(), val eqDb: List<Float> = emptyList())

/** The mixer a song plays with: its own mix over the general one, or the general one. */
fun MixerConfig.forSong(mix: SongMix?): MixerConfig =
    if (mix == null) this else copy(strips = mix.strips.ifEmpty { strips }, eqDb = mix.eqDb.ifEmpty { eqDb })

fun MixerConfig.toSongMix() = SongMix(strips, eqDb)
