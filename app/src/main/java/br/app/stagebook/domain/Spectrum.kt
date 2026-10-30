package br.app.stagebook.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Espectro do backing que o motor mede (engine.c: SP_BINS faixas logarítmicas de SP_LO a SP_HI Hz) e
 * a resposta do equalizador, com as mesmas contas dos filtros do motor (eq_design), para a tela somar
 * o EQ a cada faixa: o motor mede antes do EQ.
 */
object Spectrum {
    const val BINS = 48
    private const val LO = 30.0
    private const val HI = 16_000.0
    const val FLOOR_DB = -72f
    const val TOP_DB = 0f
    private const val RATE = 48_000.0

    /** Frequências dos 5 faders do EQ (as do motor). */
    val EQ_FREQ = doubleArrayOf(80.0, 250.0, 1000.0, 3500.0, 10_000.0)

    /** Centro (Hz) de cada faixa. */
    val centers: DoubleArray = DoubleArray(BINS) { b ->
        val lo = LO * (HI / LO).pow(b.toDouble() / BINS)
        val hi = LO * (HI / LO).pow((b + 1).toDouble() / BINS)
        sqrt(lo * hi)
    }

    fun fraction(db: Float): Float = ((db - FLOOR_DB) / (TOP_DB - FLOOR_DB)).coerceIn(0f, 1f)

    /**
     * Posição horizontal (0..1) de uma frequência na linha dos 5 faders, que dividem a largura em
     * partes iguais: cada fader no centro da sua parte, e entre eles a escala é logarítmica.
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

    /** Ganho (dB) do EQ inteiro em cada faixa, para os ganhos dados (5 valores em dB). */
    fun eqResponseDb(gainsDb: List<Float>): FloatArray {
        val coefs = gainsDb.mapIndexed { i, g -> design(i, g.toDouble()) }
        return FloatArray(BINS) { b ->
            val w = 2 * PI * centers[b] / RATE
            coefs.sumOf { c -> magnitudeDb(c, w) }.toFloat()
        }
    }

    // RBJ, igual ao eq_design do engine.c: prateleiras nas pontas (S = 1), sinos com Q = 1
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
