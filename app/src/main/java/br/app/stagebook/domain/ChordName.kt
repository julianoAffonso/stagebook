package br.app.stagebook.domain

/**
 * A chord's name read once: the root, its accidental, the rest ([suffix]: m, 7, 7M, 4, (9), 5+, /9…) and the bass.
 * The one reading every chord feature uses (stage, editor, key, transposition, harmonic field, diagrams, import;
 * user, 01/11: "tudo que mexe com acorde" follows the same rule). [ChordTheory] turns it into notes.
 */
data class ChordName(
    val root: String, val accidental: String, val suffix: String, val bass: String = "", val bassAccidental: String = "",
    // "E*": a mark the chart explains (a different way to play it, a tab or a quick note on how to form it; user,
    // 01/11). It stays written; the notes are the chord's
    val mark: String = "",
) {
    /** Root and bass as pitch classes (0 = C … 11 = B); the bass is the root when there's none. */
    val rootPc: Int get() = pc(root, accidental)
    val bassPc: Int get() = if (bass.isEmpty()) rootPc else pc(bass, bassAccidental)

    /** The chord's notes ([ChordTheory]). */
    val notes: ChordTheory.Notes get() = ChordTheory.notes(this)

    override fun toString(): String = root + accidental + suffix + (if (bass.isEmpty()) "" else "/$bass$bassAccidental") + mark

    companion object {
        // note, accidental, the rest (m, 7, maj7, sus4, add9, dim, aug, °, +, 7M, (9), (b5)…) and the bass. A slash before
        // a number is an extension, as the chord charts write it (A7/4, C#7/9, G6/9, D7(2/4)); before a note it's the bass
        private val PATTERN = Regex("""([A-G])([#b♯♭]?)((?:maj|min|dim|aug|sus|add|m|M|°|º|\+|-|\d|\(|\)|#|b|♯|♭|,|/(?=\d))*)(?:/([A-G])([#b♯♭]?))?(\*+)?""")
        private val NOTES = mapOf("C" to 0, "D" to 2, "E" to 4, "F" to 5, "G" to 7, "A" to 9, "B" to 11)

        /** The chord in [text] (the whole text, spaces around ignored), or null if it isn't a chord. */
        fun parse(text: String): ChordName? = PATTERN.matchEntire(text.trim())?.groupValues?.let { g -> ChordName(g[1], g[2], g[3], g[4], g[5], g[6]) }

        private fun pc(letter: String, acc: String): Int =
            (NOTES.getValue(letter) + when (acc) { "#", "♯" -> 1; "b", "♭" -> -1; else -> 0 } + 12) % 12
    }
}
