package br.app.stagebook.domain

import br.app.stagebook.data.model.Instrument

/**
 * Como montar um acorde no instrumento: posições nas cordas (violão/guitarra, ukulele, cavaquinho) ou as notas
 * no teclado, da mais fácil para a mais difícil. Geradas pelas notas do acorde ([ChordTheory]), sem tabela pronta.
 */
object Voicings {
    /** Uma forma no braço: casa de cada corda, da mais grave para a mais aguda (-1 = não toca, 0 = solta). */
    data class Shape(val frets: List<Int>) {
        /** Primeira casa do desenho: 1 se cabe nas 4 primeiras casas, senão a menor casa apertada. */
        val baseFret: Int get() = frets.filter { it > 0 }.let { f -> if (f.isEmpty() || f.max() <= 4) 1 else f.min() }
    }

    // afinações em notas MIDI, da corda mais grave (no braço) para a mais aguda
    private val tunings = mapOf(
        Instrument.VIOLAO to intArrayOf(40, 45, 50, 55, 59, 64),     // E A D G B E
        Instrument.GUITARRA to intArrayOf(40, 45, 50, 55, 59, 64),
        Instrument.UKULELE to intArrayOf(67, 60, 64, 69),            // G C E A (sol agudo)
        Instrument.CAVAQUINHO to intArrayOf(62, 67, 71, 74),         // D G B D
    )

    fun strings(i: Instrument): Int = tunings[i]?.size ?: 0

    /** As notas (MIDI) que o desenho faz soar, da corda grave para a aguda; [capo] sobe tudo (a nota real com capo). */
    fun midi(shape: Shape, instrument: Instrument, capo: Int = 0): List<Int> {
        val tuning = tunings[instrument] ?: return emptyList()
        return shape.frets.mapIndexedNotNull { i, f -> if (f < 0 || i >= tuning.size) null else tuning[i] + f + capo }
    }

    /** Formas do acorde no instrumento de cordas, das mais fáceis para as mais difíceis (vazio no teclado). */
    fun shapes(chord: String, instrument: Instrument, max: Int = 12): List<Shape> {
        val tuning = tunings[instrument] ?: return emptyList()
        val notes = ChordTheory.notes(chord) ?: return emptyList()
        val six = tuning.size == 6
        val found = HashMap<List<Int>, Double>()
        val current = IntArray(tuning.size)
        for (window in 1..10) {
            val options = tuning.map { open ->
                buildList {
                    if (six) add(-1)
                    if ((open % 12) in notes.tones) add(0)
                    for (f in window..window + 3) if ((open + f) % 12 in notes.tones) add(f)
                }
            }
            fun walk(s: Int) {
                if (s == tuning.size) {
                    score(current, tuning, notes, six)?.let { sc ->
                        val key = current.toList()
                        if (sc < (found[key] ?: Double.MAX_VALUE)) found[key] = sc
                    }
                    return
                }
                for (o in options[s]) { current[s] = o; walk(s + 1) }
            }
            walk(0)
        }
        return found.entries.sortedBy { it.value }.take(max).map { Shape(it.key) }
    }

    /** Nota de cada forma é possível de tocar? Devolve a dificuldade (menor = mais fácil) ou null se não serve. */
    private fun score(f: IntArray, tuning: IntArray, notes: ChordTheory.Notes, six: Boolean): Double? {
        val n = f.size
        val sounding = f.indices.filter { f[it] >= 0 }
        if (sounding.size < (if (six) 4 else n)) return null
        // corda abafada só nas graves, em sequência (ninguém abafa uma corda do meio)
        if (six) {
            val firstSounding = sounding.first()
            if (sounding.size != n - firstSounding) return null
        }
        val pcs = sounding.map { (tuning[it] + f[it]) % 12 }.toSet()
        if (!pcs.containsAll(notes.essential)) return null
        // no violão a nota mais grave é o baixo do acorde (a tônica, ou o baixo depois da barra)
        if (six && (tuning[sounding.first()] + f[sounding.first()]) % 12 != notes.bass) return null
        val fretted = sounding.filter { f[it] > 0 }
        val lo = fretted.minOfOrNull { f[it] } ?: 0
        val hi = fretted.maxOfOrNull { f[it] } ?: 0
        if (hi - lo > 3) return null
        // pestana só quando faltam dedos (mais de 4 notas apertadas): a menor casa em várias cordas, sem corda solta
        // no meio, vira um dedo só
        val fingers = if (fretted.size <= 4) fretted.size else if (needsBarre(f)) 1 + fretted.count { f[it] > lo } else fretted.size
        if (fingers > 4) return null
        val muted = n - sounding.size
        val open = sounding.count { f[it] == 0 }
        val noFifth = if ((notes.root + 7) % 12 in notes.tones && (notes.root + 7) % 12 !in pcs) 0.5 else 0.0
        // abertura dos dedos (da menor à maior casa) pesa: x50232 é tocável, mas ninguém monta D assim
        return lo * 3.0 + muted * 2.0 + fingers + (hi - lo) - open * 0.5 + noFifth
    }

    /** A forma precisa de pestana? Mais de 4 notas apertadas e a menor casa em várias cordas, sem solta no meio. */
    fun needsBarre(f: IntArray): Boolean {
        val fretted = f.indices.filter { f[it] > 0 }
        if (fretted.size <= 4) return false
        val lo = fretted.minOf { f[it] }
        val atLo = fretted.filter { f[it] == lo }
        return atLo.size >= 2 && (atLo.first()..atLo.last()).none { f[it] == 0 }
    }

    /** Teclado: as notas do acorde (MIDI), em posição fundamental e nas inversões. */
    fun keys(chord: String): List<List<Int>> {
        val notes = ChordTheory.notes(chord) ?: return emptyList()
        val upper = (notes.tones - notes.bass).sortedBy { (it - notes.root + 12) % 12 }
        val base = listOf(notes.bass) + upper
        return base.indices.take(3).map { rot ->
            val order = base.drop(rot) + base.take(rot)
            var last = 59
            order.map { pc -> var m = 60 + pc; while (m <= last) m += 12; last = m; m }.map { it - if (order.first() >= 5) 12 else 0 }
        }.distinct()
    }

    // ---- {define} do ChordPro: a forma escolhida guardada na música ----

    /** `{define-guitar: G base-fret 1 frets 3 2 0 0 0 3}` (casas relativas à base, x = não toca). */
    fun define(instrument: Instrument, chord: String, shape: Shape): String {
        val base = shape.baseFret
        val frets = shape.frets.joinToString(" ") { if (it < 0) "x" else if (it == 0) "0" else (it - base + 1).toString() }
        return "{define-${instrument.chordPro}: $chord base-fret $base frets $frets}"
    }

    /** `{define-keyboard: C keys 0 4 7}` (notas a partir da primeira, em semitons). */
    fun defineKeys(chord: String, keys: List<Int>): String =
        "{define-keyboard: $chord keys ${keys.joinToString(" ") { (it - keys.first()).toString() }}}"

    private val defineLine = Regex("""^\s*\{\s*define-(\w+)\s*:\s*(\S+)\s+(.*)}\s*$""")

    /** A forma guardada na música para o acorde, se houver (corda) — null se não houver. */
    fun savedShape(text: String, instrument: Instrument, chord: String): Shape? {
        val m = text.lineSequence().mapNotNull { defineLine.matchEntire(it) }
            .firstOrNull { it.groupValues[1] == instrument.chordPro && it.groupValues[2] == chord } ?: return null
        val parts = m.groupValues[3].split(Regex("\\s+"))
        val base = parts.getOrNull(parts.indexOf("base-fret") + 1)?.toIntOrNull() ?: 1
        val i = parts.indexOf("frets")
        if (i < 0) return null
        val frets = parts.drop(i + 1).take(strings(instrument)).map { if (it == "x" || it == "X") -1 else it.toIntOrNull()?.let { r -> if (r == 0) 0 else r + base - 1 } ?: return null }
        return if (frets.size == strings(instrument)) Shape(frets) else null
    }

    /** As notas guardadas na música para o acorde no teclado, se houver. */
    fun savedKeys(text: String, chord: String): List<Int>? {
        val m = text.lineSequence().mapNotNull { defineLine.matchEntire(it) }
            .firstOrNull { it.groupValues[1] == "keyboard" && it.groupValues[2] == chord } ?: return null
        val parts = m.groupValues[3].split(Regex("\\s+"))
        val i = parts.indexOf("keys")
        if (i < 0) return null
        val rel = parts.drop(i + 1).mapNotNull { it.toIntOrNull() }
        val first = 60 + (ChordTheory.notes(chord)?.bass ?: 0)
        return rel.map { first + it }
    }

    /** Grava (troca) a forma escolhida na música, no começo do texto. */
    fun saveDefine(text: String, instrument: Instrument, chord: String, line: String): String {
        val lines = text.split('\n').filterNot { l ->
            defineLine.matchEntire(l)?.let { it.groupValues[1] == instrument.chordPro && it.groupValues[2] == chord } == true
        }
        return (listOf(line) + lines).joinToString("\n")
    }
}
