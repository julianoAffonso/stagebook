package com.juliano.stagebook.domain

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/** Normalização de um canal (ou par): não mexe, volume percebido (LUFS) ou pico. */
@Serializable
enum class NormMode { NONE, VOLUME, PEAK }

/** Um canal do mixer. Nos canais 1, 3, 5 e 7, [stereo] junta o canal com o seguinte num par (o seguinte segue este). */
@Immutable
@Serializable
data class MixChannel(
    val name: String = "",
    val gainDb: Float = 0f,
    val on: Boolean = true,
    val norm: NormMode = NormMode.NONE,
    val eq: Boolean = false,
    val center: Boolean = false,   // divide igual para os dois lados, ignorando o lado em que veio gravado
    val stereo: Boolean = false,
)

/** Uma saída de áudio: a do aparelho ou uma interface USB ([key] = "usb:vid:pid"), com o nome e quantas saídas tem. */
@Immutable
@Serializable
data class OutputInfo(val key: String = DEVICE, val name: String = "", val count: Int = 2) {
    companion object {
        const val DEVICE = "device"
    }
}

/**
 * Mixer do app (vale para todos os backing tracks): um canal por canal do arquivo. Sem configuração, cada canal vai
 * para a saída de mesmo número, sem ganho, sem normalização e sem EQ: nada é adivinhado.
 */
@Immutable
@Serializable
data class MixerConfig(
    val channels: Int = 2,
    val strips: List<MixChannel> = List(MAX) { MixChannel() },
    val eqOn: Boolean = false,
    val eqDb: List<Float> = List(EQ_BANDS) { 0f },
    // a saída em uso (ou a última usada): é a que os Ajustes mostram e editam
    val output: OutputInfo = OutputInfo(),
    // roteamento guardado por saída (chave da OutputInfo): a primeira saída de cada canal; -1 = a de mesmo número
    val routes: Map<String, List<Int>> = emptyMap(),
) {
    fun strip(i: Int): MixChannel = strips.getOrElse(i) { MixChannel() }

    /** Primeira saída (0 = saída 1) do canal [i] na saída [key]; sem escolha, a de mesmo número. */
    fun outputOf(i: Int, key: String = output.key): Int = routes[key]?.getOrNull(i)?.takeIf { it >= 0 } ?: i

    /** Escolhe a saída do canal [i] na saída em uso; as das outras interfaces ficam como estão. */
    fun withOutput(i: Int, out: Int): MixerConfig {
        val now = routes[output.key].orEmpty()
        return copy(routes = routes + (output.key to List(MAX) { if (it == i) out else now.getOrElse(it) { -1 } }))
    }

    fun withStrip(i: Int, change: (MixChannel) -> MixChannel): MixerConfig =
        copy(strips = List(MAX) { if (it == i) change(strip(it)) else strip(it) })

    /** Canais do mixer: cada um sozinho ou um par (primeiro, último) quando o primeiro é estéreo. */
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
        const val MIN_DB = -60f   // fader no fim = mudo
        const val MAX_DB = 6f
    }
}

/** Medidores ao vivo do mixer: cada canal do arquivo (já com a balística) e o espectro dos canais com EQ. */
@Immutable
data class MixerLevels(val meters: Meters, val spectrumDb: List<Float>, val live: Boolean) {
    companion object {
        val SILENT = MixerLevels(Meters.silent(MixerConfig.MAX), List(Spectrum.BINS) { Spectrum.FLOOR_DB }, live = false)
    }
}

/** Medição de um canal ou par num backing track: volume (LUFS) e pico (linear, 1 = 0 dBFS). */
data class Measure(val lufs: Float, val peak: Float)

/** O que o motor recebe: ganho e normalização de cada canal do arquivo, canais com EQ e a matriz [saída][canal]. */
class MixPlan(val gains: FloatArray, val norms: FloatArray, val eqMask: Int, val route: FloatArray)

object Mixer {
    const val TARGET_LUFS = -16f        // volume: igual entre músicas
    const val TARGET_PEAK_DB = -6f      // pico: igual entre músicas (click, guia)
    const val CEILING_DB = -1f          // volume nunca passa de -1 dBFS de pico
    const val MAX_BOOST_DB = 15f
    const val MAX_CUT_DB = -24f
    private val HALF = sqrt(0.5f)       // canal mono nos dois lados: mesmo volume percebido

    fun db(lin: Float): Float = (20 * log10(lin.coerceAtLeast(1e-9f).toDouble())).toFloat()
    fun lin(db: Float): Float = 10f.pow(db / 20f)

    /** Ganho (dB) da normalização; sem medição ainda, nada. */
    fun normDb(mode: NormMode, m: Measure?): Float {
        if (m == null || mode == NormMode.NONE) return 0f
        val g = when (mode) {
            NormMode.VOLUME -> minOf(TARGET_LUFS - m.lufs, CEILING_DB - db(m.peak))
            else -> if (m.peak > 1e-6f) TARGET_PEAK_DB - db(m.peak) else 0f
        }
        return g.coerceIn(MAX_CUT_DB, MAX_BOOST_DB)
    }

    /** Bits dos canais do arquivo de um canal do mixer (para medir o par junto). */
    fun mask(group: IntRange): Int = group.fold(0) { m, c -> m or (1 shl c) }

    /** A mistura para um arquivo com [lanes] canais; [measures] por canal do mixer (chave: o primeiro canal). */
    fun plan(cfg: MixerConfig, lanes: Int, measures: Map<Int, Measure> = emptyMap(), key: String = cfg.output.key): MixPlan {
        val n = MixerConfig.MAX
        val gains = FloatArray(n) { 1f }
        val norms = FloatArray(n) { 1f }
        val route = FloatArray(n * n)
        var eq = 0
        for (g in cfg.groups(lanes)) {
            val s = cfg.strip(g.first)
            val gain = if (!s.on || s.gainDb <= MixerConfig.MIN_DB) 0f else lin(s.gainDb)
            val norm = lin(normDb(s.norm, measures[g.first]))
            val base = cfg.outputOf(g.first, key).coerceIn(0, n - 1)
            for ((k, c) in g.withIndex()) {
                gains[c] = gain
                norms[c] = norm
                if (s.eq) eq = eq or (1 shl c)
                if (s.center) {
                    // par: soma dos dois lados no meio; mono: o canal e a saída vizinha do mesmo par de saídas
                    val left = if (g.count() == 2) base else base - base % 2
                    val w = if (g.count() == 2) 0.5f else HALF
                    for (o in left..minOf(left + 1, n - 1)) route[o * n + c] = w
                } else {
                    val o = base + k
                    if (o < n) route[o * n + c] = 1f
                }
            }
        }
        return MixPlan(gains, norms, eq, route)
    }
}
