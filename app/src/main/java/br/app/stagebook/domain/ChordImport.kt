package br.app.stagebook.domain

/**
 * Chords imported from a site (Cifra Club and the like): the text with chords on the line above becomes ChordPro, with
 * the page's key and capo in `{key}`/`{capo}`. Tablature (lines like `E|--3--|`) is left out.
 */
object ChordImport {
    private val tabLine = Regex("""^\s*[A-Ga-g][#b]?\s*\|[-0-9hpbr/\\|~x\s]*$""")
    // key and capo the way sites write them in 4 languages: "Tom: G", "Key: G", "Tono: G", "Tonalità: G"; "Capotraste
    // na 2ª casa", "Capotraste: 2ª casa", "Capo 2", "Capo: 2nd fret", "Cejilla en el 2º traste", "Capotasto al 2º tasto"
    private val capoText = Regex("""(?i)\b(?:capotraste(?:\s+na)?|capotasto\s+(?:al|sul)|cejilla(?:\s+en(?:\s+el)?)?|capo(?:\s+on)?)\s*:?\s*(\d{1,2})""")
    private val keyText = Regex("""(?i)\b(?:tom|key|tono|tonalit[àa])\s*:\s*([A-G][#b]?m?)(?![\w#])""")
    // with a capo Cifra Club says "F#m (com forma de Em)" or "F#m (in the shape of Em)": the chart is in the shapes (Em),
    // which is what `{key}` holds
    private val shapeKey = Regex("""(?i)\b(?:com forma de|in the shape of)\s+([A-G][#b]?m?)(?![\w#])""")
    // the fret alone, as the page's capo button says it: "2", "2ª casa", "2nd fret"
    private val bareFret = Regex("""(?i)\s*(\d{1,2})\s*(?:[ªº°]|st|nd|rd|th)?(?:\s+(?:casa|traste|tasto|fret))?\s*""")

    /** Key and capo the page shows (to keep them in the lyrics only profile too). */
    fun keyOf(vararg texts: String): String = texts.firstNotNullOfOrNull { t -> (shapeKey.find(t) ?: keyText.find(t))?.groupValues?.get(1)?.takeIf(Chords::isChord) ?: t.trim().takeIf(Chords::isChord) }.orEmpty()

    /** BPM: the page's, or a line "BPM 120"/"Tempo: 120"/"{tempo: 120}" in the text itself; 0 = none. */
    fun bpmOf(page: String, text: String): Int {
        val fromText = bpmText.find(text)?.let { m -> m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } }
        return listOf(page.trim(), fromText.orEmpty()).firstNotNullOfOrNull { it.toIntOrNull()?.takeIf { v -> v in Chords.MIN_TEMPO..Chords.MAX_TEMPO } } ?: 0
    }

    private val bpmText = Regex("""(?im)^\s*(?:\{\s*tempo\s*:\s*(\d{2,3})\s*\}|(?:bpm|tempo)\s*[:=]?\s*(\d{2,3})|(\d{2,3})\s*bpm)\s*$""")

    fun capoOf(vararg texts: String): Int = texts.firstNotNullOfOrNull { t -> (capoText.find(t) ?: bareFret.matchEntire(t))?.groupValues?.get(1)?.toIntOrNull() }?.takeIf { it in 1..12 } ?: 0

    // sites pad the chord lines with non-breaking and thin spaces; Java's \s doesn't see them, so the line read as
    // lyrics and only the lyrics came in (user, 31/10)
    private val oddSpaces = Regex("[\u00A0\u1680\u2000-\u200A\u202F\u205F\u3000]")
    // and invisible marks at the start of every line (Cifra Club, Fullgás, 31/10): "\u200BBm" isn't a chord
    private val invisible = Regex("[\u200B-\u200D\u2060\uFEFF]")

    /** The text with every kind of space as a plain one, invisible marks gone (and tabs as 4 spaces, keeping the columns). */
    fun plainSpaces(text: String): String = text.replace(invisible, "").replace(oddSpaces, " ").replace("\t", "    ")

    /** Does the page have chords? (two or more chords-only lines) */
    fun hasChords(text: String): Boolean = plainSpaces(text).lineSequence().count(Chords::isChordLine) >= 2

    /**
     * The page's chords as ChordPro. [key] and [capo] come from the page (text like "Tom: G", "Capotraste na 2ª casa"),
     * and when missing they're looked for in the text itself.
     */
    fun toChordPro(text: String, key: String = "", capo: String = ""): String {
        // the "Tom: G" and "Capotraste na 2ª casa" lines become directives: removed before converting (otherwise the G
        // from the key line would land above the lyrics below)
        val all = plainSpaces(text).replace("\r", "").split('\n').map { it.replace(TextPatterns.TRAILING_SPACE, "") }
        // marked tablature blocks ({start_of_tab}…{end_of_tab}, T22) pass as they are; the rest is converted
        val inTab = Tablature.tabLines(all.joinToString("\n"))
        val parts = ArrayList<String>()
        val plain = ArrayList<String>()
        fun flush() {
            if (plain.isEmpty()) return
            parts += Chords.fromChordsOverLyrics(plain.filterNot { tabLine.matches(it) || keyText.matches(it.trim()) || shapeKey.containsMatchIn(it) || (capoText.containsMatchIn(it) && it.length < 40) }.joinToString("\n"))
            plain.clear()
        }
        var block = false
        all.forEachIndexed { i, line ->
            when {
                Tablature.isStart(line) -> { flush(); block = true; parts += line }
                Tablature.isEnd(line) -> { block = false; parts += line }
                block && inTab[i] -> parts += line
                else -> plain += line
            }
        }
        flush()
        var body = parts.joinToString("\n").replace(TextPatterns.MANY_BLANK, "\n\n").trim()
        val k = keyOf(key, text)
        val c = capoOf(capo, text)
        if (c in 1..12) body = Chords.setDirective(body, "capo", c.toString())
        if (Chords.isChord(k)) body = Chords.setDirective(body, "key", k)
        return body
    }
}
