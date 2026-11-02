package br.app.stagebook.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The backing spectrum the engine measures (engine.c: SP_BINS log bands from SP_LO to SP_HI Hz) and the equalizer
 * response, with the same math as the engine's filters (eq_design), so the screen adds the EQ to each band: the engine
 * measures before the EQ.
 */
object Spectrum {
    const val BINS = 48
    private const val LO = 30.0
    private const val HI = 16_000.0
    const val FLOOR_DB = -72f
    const val TOP_DB = 0f
    private const val RATE = 48_000.0

    /** Frequencies of the 5 EQ faders (the engine's). */
    val EQ_FREQ = doubleArrayOf(80.0, 250.0, 1000.0, 3500.0, 10_000.0)

    /** Center (Hz) of each band. */
    val centers: DoubleArray = DoubleArray(BINS) { b ->
        val lo = LO * (HI / LO).pow(b.toDouble() / BINS)
        val hi = LO * (HI / LO).pow((b + 1).toDouble() / BINS)
        sqrt(lo * hi)
    }

    fun fraction(db: Float): Float = ((db - FLOOR_DB) / (TOP_DB - FLOOR_DB)).coerceIn(0f, 1f)

    /**
     * Horizontal position (0..1) of a frequency on the row of 5 faders, which split the width into equal parts: each
     * fader at the center of its part, with a log scale between them.
     */
    fun x(freq: Double): Float {
        val lf = log10(freq)
        val l = EQ_FREQ.map { log10(it) }
        val i = when {
            lf <= l[1] -> 0
            lf >= l[3] -> 3
            else -> (1 until 4).first { lf < l[it + 1] }
        }
        val t = (lf - l[i]) / (l[i + 1] - l[i])
        return ((i + t + 0.5) / EQ_FREQ.size).toFloat()
    }

    /** Gain (dB) of the whole EQ at each band, for the given gains (5 values in dB). */
    fun eqResponseDb(gainsDb: List<Float>): FloatArray {
        val coefs = gainsDb.mapIndexed { i, g -> design(i, g.toDouble()) }
        return FloatArray(BINS) { b ->
            val w = 2 * PI * centers[b] / RATE
            coefs.sumOf { c -> magnitudeDb(c, w) }.toFloat()
        }
    }

    // RBJ, same as engine.c's eq_design: shelves at the ends (S = 1), bells with Q = 1
    private fun design(i: Int, gainDb: Double): DoubleArray {
        val a = 10.0.pow(gainDb / 40)
        val w = 2 * PI * EQ_FREQ[i] / RATE
        val cs = cos(w)
        val sn = sin(w)
        val r: DoubleArray
        if (i == 0 || i == EQ_FREQ.size - 1) {
            val al = sn / 2 * sqrt(2.0)
            val sq = 2 * sqrt(a) * al
            r = if (i == 0) {
                doubleArrayOf(
                    a * ((a + 1) - (a - 1) * cs + sq), 2 * a * ((a - 1) - (a + 1) * cs), a * ((a + 1) - (a - 1) * cs - sq),
                    (a + 1) + (a - 1) * cs + sq, -2 * ((a - 1) + (a + 1) * cs), (a + 1) + (a - 1) * cs - sq,
                )
            } else {
                doubleArrayOf(
                    a * ((a + 1) + (a - 1) * cs + sq), -2 * a * ((a - 1) + (a + 1) * cs), a * ((a + 1) + (a - 1) * cs - sq),
                    (a + 1) - (a - 1) * cs + sq, 2 * ((a - 1) - (a + 1) * cs), (a + 1) - (a - 1) * cs - sq,
                )
            }
        } else {
            val al = sn / 2
            r = doubleArrayOf(1 + al * a, -2 * cs, 1 - al * a, 1 + al / a, -2 * cs, 1 - al / a)
        }
        return r
    }

    private fun magnitudeDb(c: DoubleArray, w: Double): Double {
        val nr = c[0] + c[1] * cos(w) + c[2] * cos(2 * w)
        val ni = -(c[1] * sin(w) + c[2] * sin(2 * w))
        val dr = c[3] + c[4] * cos(w) + c[5] * cos(2 * w)
        val di = -(c[4] * sin(w) + c[5] * sin(2 * w))
        return 10 * log10((nr * nr + ni * ni) / (dr * dr + di * di))
    }
}
