package br.app.stagebook.domain

/**
 * A chord's notes from its name (`G`, `Am7`, `C7(9)`, `F#m7(b5)`, `D/F#`, `A4`, `G7M`), as pitch classes (0 = C … 11 =
 * B). Follows the Brazilian chord chart convention: a lone `9` is an added ninth (no seventh), `4` is a suspended
 * fourth, `7M` is a major seventh.
 */
object ChordTheory {
    // built once, not on every chord (they ran for each diagram and harmonic field; audit 30/10)
    // the sign after the number too (13-, 9+, 11+), as Cifra Club writes it; 7+ is the major seventh (user, 01/11)
    private val REST = Regex("""maj7|7M|7\+|M|add9|add11|add13|add2|add4|sus2|sus4|sus|b13|13-|#11|11\+|b9|9-|#9|9\+|b5|#5|-5|5-|5\+|13|11|9|7|6|5|4|2|\+""")
    // a slash between numbers is one more extension (A7/4, D7(2/4))
    private val SEPARATORS = Regex("""[()\s,/]""")

    /**
     * [root] and [bass] in 0..11; [tones] = all notes; [essential] = the ones that can't be missing (the fifth can);
     * [intervals] = the chord's own intervals from the root, without the bass (the key, the harmonic field and the
     * transposition read the chord through them, the same reading as the diagram; user, 01/11).
     */
    data class Notes(val root: Int, val bass: Int, val tones: Set<Int>, val essential: Set<Int>, val intervals: Set<Int> = emptySet())

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

        // intervals from the root (semitones)
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
        // the rest, in any order: 7, 7M, 6, 9, 11, 13, sus, add, (b5), (#5), (9), 5 (power chord)…
        val rest = REST
        var power = false
        SEPARATORS.replace(s, " ").split(' ').filter { it.isNotEmpty() }.forEach { part ->
            var p = part
            while (p.isNotEmpty()) {
                val t = rest.find(p)?.takeIf { it.range.first == 0 }?.value ?: break
                p = p.removePrefix(t)
                when (t) {
                    "maj7", "7M", "7+", "M" -> seventh = 11
                    "7" -> if (seventh == null) seventh = 10
                    "6" -> extra += 9
                    "9", "add9", "add2", "2" -> extra += 2
                    "b9", "9-" -> extra += 1
                    "#9", "9+" -> extra += 3
                    "11", "add11", "add4" -> extra += 5
                    "#11", "11+" -> extra += 6
                    "13", "add13" -> extra += 9
                    "b13", "13-" -> extra += 8
                    "sus2" -> third = 2
                    "sus4", "sus", "4" -> third = 5
                    "b5", "-5", "5-" -> { fifth = 6; fifthEssential = true }
                    "#5", "5+", "+" -> { fifth = 8; fifthEssential = true }
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
        return Notes(root, bass, intervals.map(::abs).toSet() + bass, essential.map(::abs).toSet() + bass, intervals)
    }
}
