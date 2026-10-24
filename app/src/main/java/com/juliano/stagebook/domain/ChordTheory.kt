package com.juliano.stagebook.domain

/**
 * As notas de um acorde a partir do nome (`G`, `Am7`, `C7(9)`, `F#m7(b5)`, `D/F#`, `A4`, `G7M`), em classes de
 * altura (0 = C … 11 = B). Segue o costume brasileiro de cifra: `9` sozinho é nona adicionada (sem sétima),
 * `4` é suspenso de quarta, `7M` é sétima maior.
 */
object ChordTheory {
    /** [root] e [bass] em 0..11; [tones] = todas as notas; [essential] = as que não podem faltar (a quinta pode). */
    data class Notes(val root: Int, val bass: Int, val tones: Set<Int>, val essential: Set<Int>)

    private val names = mapOf("C" to 0, "D" to 2, "E" to 4, "F" to 5, "G" to 7, "A" to 9, "B" to 11)
    private val head = Regex("""^([A-G])([#b♯♭]?)(.*?)(?:/([A-G])([#b♯♭]?))?$""")

    private fun pc(letter: String, acc: String): Int {
        val base = names.getValue(letter)
        return (base + when (acc) { "#", "♯" -> 1; "b", "♭" -> -1; else -> 0 } + 12) % 12
    }

    fun notes(chord: String): Notes? {
        if (!Chords.isChord(chord)) return null
        val m = head.matchEntire(chord.trim()) ?: return null
        val root = pc(m.groupValues[1], m.groupValues[2])
        val bass = if (m.groupValues[4].isNotEmpty()) pc(m.groupValues[4], m.groupValues[5]) else root
        var s = m.groupValues[3].replace("♯", "#").replace("♭", "b")

        // intervalos a partir da tônica (semitons)
        var third: Int? = 4
        var fifth = 7
        var fifthEssential = false
        val extra = linkedSetOf<Int>()
        var seventh: Int? = null

        fun take(token: String): Boolean = if (s.startsWith(token)) { s = s.removePrefix(token); true } else false

        when {
            take("maj7") || take("7M") || take("M7") -> seventh = 11
            take("dim7") || take("°7") || take("º7") -> { third = 3; fifth = 6; fifthEssential = true; seventh = 9 }
            take("dim") || take("°") || take("º") -> { third = 3; fifth = 6; fifthEssential = true }
            take("aug") || take("+") -> { fifth = 8; fifthEssential = true }
            take("min") || (s.startsWith("m") && !s.startsWith("maj") && take("m")) -> third = 3
        }
        // o resto, em qualquer ordem: 7, 7M, 6, 9, 11, 13, sus, add, (b5), (#5), (9), 5 (power chord)…
        val rest = Regex("""maj7|7M|M|add9|add11|add13|add2|add4|sus2|sus4|sus|b13|#11|b9|#9|b5|#5|-5|5-|5\+|13|11|9|7|6|5|4|2""")
        var power = false
        Regex("""[()\s,]""").replace(s, " ").split(' ').filter { it.isNotEmpty() }.forEach { part ->
            var p = part
            while (p.isNotEmpty()) {
                val t = rest.find(p)?.takeIf { it.range.first == 0 }?.value ?: break
                p = p.removePrefix(t)
                when (t) {
                    "maj7", "7M", "M" -> seventh = 11
                    "7" -> if (seventh == null) seventh = 10
                    "6" -> extra += 9
                    "9", "add9", "add2", "2" -> extra += 2
                    "b9" -> extra += 1
                    "#9" -> extra += 3
                    "11", "add11", "add4" -> extra += 5
                    "#11" -> extra += 6
                    "13", "add13" -> extra += 9
                    "b13" -> extra += 8
                    "sus2" -> third = 2
                    "sus4", "sus", "4" -> third = 5
                    "b5", "-5", "5-" -> { fifth = 6; fifthEssential = true }
                    "#5", "5+" -> { fifth = 8; fifthEssential = true }
                    "5" -> power = true
                }
            }
        }
        if (power && third == 4 && extra.isEmpty() && seventh == null) third = null
        val intervals = buildSet {
            add(0)
            third?.let(::add)
            add(fifth)
            seventh?.let(::add)
            addAll(extra)
        }
        val essential = buildSet {
            add(0)
            third?.let(::add)
            seventh?.let(::add)
            addAll(extra)
            if (fifthEssential || power) add(fifth)
        }
        fun abs(i: Int) = (root + i) % 12
        return Notes(root, bass, intervals.map(::abs).toSet() + bass, essential.map(::abs).toSet() + bass)
    }
}
