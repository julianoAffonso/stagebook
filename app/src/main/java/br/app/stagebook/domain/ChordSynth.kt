package br.app.stagebook.domain

import br.app.stagebook.data.model.ChordPlayMode
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * O som do acorde tocado no palco, gerado aqui (sem arquivos de áudio). Cordas (violão, guitarra, ukulele, cavaquinho):
 * Karplus-Strong, a corda dedilhada clássica; teclado: alguns harmônicos com queda. [ChordPlayMode]: dedilhado (uma nota
 * de cada vez), junto (as cordas quase juntas, como uma batida) ou os dois (dedilhado e depois junto).
 */
object ChordSynth {
    const val RATE = 44_100
    private const val ARPEGGIO_GAP_S = 0.22
    private const val STRUM_GAP_S = 0.018
    private const val RING_S = 1.6
    private const val VOLUME = 0.32

    fun hz(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    /** As notas (MIDI, da mais grave para a mais aguda) tocadas no modo escolhido; [keyboard] = som de teclado. */
    fun render(notes: List<Int>, mode: ChordPlayMode, keyboard: Boolean, rate: Int = RATE): ShortArray {
        if (notes.isEmpty()) return ShortArray(0)
        val starts = ArrayList<Pair<Double, Int>>()   // (segundos, nota)
        var t = 0.0
        if (mode != ChordPlayMode.CHORD) {
            notes.forEach { starts += t to it; t += ARPEGGIO_GAP_S }
            t += if (mode == ChordPlayMode.BOTH) 0.35 else 0.0
        }
        if (mode != ChordPlayMode.ARPEGGIO) notes.forEachIndexed { i, n -> starts += (t + i * STRUM_GAP_S) to n }
        val total = ((starts.maxOf { it.first } + RING_S) * rate).toInt()
        val mix = DoubleArray(total)
        val random = Random(7)
        starts.forEach { (start, note) ->
            val from = (start * rate).toInt()
            val voice = if (keyboard) keyNote(hz(note), rate) else pluck(hz(note), rate, random)
            for (i in voice.indices) if (from + i < total) mix[from + i] += voice[i]
        }
        // o acorde junto soma várias cordas: o volume cai para não estourar
        val peak = mix.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-9)
        val gain = VOLUME / maxOf(peak, 0.5)
        return ShortArray(total) { (mix[it] * gain * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort() }
    }

    // corda dedilhada (Karplus-Strong): ruído num laço do tamanho do período, filtrado a cada volta
    private fun pluck(hz: Double, rate: Int, random: Random): DoubleArray {
        val n = (RING_S * rate).toInt()
        val period = (rate / hz).toInt().coerceAtLeast(2)
        val buf = DoubleArray(period) { random.nextDouble(-1.0, 1.0) }
        val out = DoubleArray(n)
        var i = 0
        for (k in 0 until n) {
            val next = (i + 1) % period
            val v = buf[i]
            out[k] = v
            buf[i] = 0.996 * 0.5 * (v + buf[next])
            i = next
        }
        return out
    }

    // nota de teclado: fundamental e dois harmônicos, com ataque rápido e queda
    private fun keyNote(hz: Double, rate: Int): DoubleArray {
        val n = (RING_S * rate).toInt()
        return DoubleArray(n) { k ->
            val s = k.toDouble() / rate
            val env = minOf(1.0, s * 200) * exp(-s * 2.2)
            env * (sin(2 * PI * hz * s) + 0.4 * sin(4 * PI * hz * s) + 0.15 * sin(6 * PI * hz * s))
        }
    }
}
