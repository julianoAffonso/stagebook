package br.app.stagebook.domain

/**
 * Chords imported from a site (Cifra Club and the like): the text with chords on the line above becomes ChordPro, with
 * the page's key and capo in `{key}`/`{capo}`. Tablature (lines like `E|--3--|`) is left out.
 */
object ChordImport {
    private val tabLine = Regex("""^\s*[A-Ga-g][#b]?\s*\|[-0-9hpbr/\\|~x\s]*$""")
    // Every rule for reading a page lives here, by what the text says and never by site (the user can add any site).
    // A label can touch the word before it: on pages that join their fields it reads "EKey: F#mCapo:"
    private const val LABEL_START = """(?:(?<!\p{L})|(?<=\p{Ll})(?=\p{Lu})|(?<=\p{Lu})(?=\p{Lu}\p{Ll}))"""
    // 4 languages: "Capotraste na 2ª casa", "Capotraste: 2ª casa", "Capo 2", "Capo: 2nd fret", "Cejilla en el 2º traste",
    // "Capotasto al 2º tasto"; the fret may be on the next line
    private val capoText = Regex("""$LABEL_START(?i:capotraste(?:\s+na(?:\s+casa)?)?|capotasto(?:\s+(?:al|sul))?|cejilla(?:\s+en(?:\s+el)?)?|capo(?:\s+on)?)\s*:?\s*(\d{1,2})(?!\d)""")
    // "Tom: G", "Key: A minor", "Tono: F♯m", "Tonalidad: Dó maior", "Tonalità: G"; the value may be on the next line. It has
    // to end there: "Tom: A música é boa" is a comment, not a key
    private const val KEY_VALUE = """([A-G]|(?i:d[oó]|r[eé]|mi|f[aá]|sol|l[aá]|si))([#b♯♭]?)(m?)(?:[ \t]+((?i:minor|menor|minore|major|maior|mayor|maggiore)))?"""
    // the key may name the chart's shapes in parentheses, in any language: "F#m (com forma de Em)", "F#m (in the shape of Em)"
    private const val SHAPE = """(?:\s*\([^)\n]*\s([A-G][#b]?m?)\s*\))?"""
    private const val KEY_END = """(?=[ \t]*$|[ \t]*[(,;|·/–\}-]|[ \t]+(?i:capo|cejilla|bpm|tempo|tuning|afina)|\p{Lu}\p{Ll})"""
    private val keyText = Regex("""(?m)$LABEL_START(?i:tom|key|tono|tonalidade?|tonalit[àa])[ \t]*:[ \t]*\n?[ \t]*$KEY_VALUE$SHAPE$KEY_END""")
    private val bareKey = Regex("""\s*$KEY_VALUE$SHAPE\s*""")
    private val solfege = mapOf("do" to "C", "re" to "D", "mi" to "E", "fa" to "F", "sol" to "G", "la" to "A", "si" to "B")
    private val minorWords = setOf("minor", "menor", "minore")

    private data class StatedKey(val key: String, val shape: String)

    private fun stated(m: MatchResult): StatedKey {
        val (note, accidental, m3, word, shape) = m.destructured
        val letter = if (note.length == 1) note else solfege.getValue(TextKey.of(note))
        val minor = m3.isNotEmpty() || word.lowercase() in minorWords
        return StatedKey(letter + accidental.replace('♯', '#').replace('♭', 'b') + if (minor) "m" else "", shape)
    }

    private fun statedKey(texts: Array<out String>): StatedKey? =
        texts.firstNotNullOfOrNull { t -> (keyText.find(t) ?: bareKey.matchEntire(t))?.let(::stated)?.takeIf { Chords.isChord(it.key) } }

    // the fret alone: "2", "2ª casa", "2nd fret"
    private val bareFret = Regex("""(?i)\s*(\d{1,2})\s*(?:[ªº°]|st|nd|rd|th)?(?:\s+(?:casa|traste|tasto|fret))?\s*""")

    /** Key and capo the page shows (to keep them in the lyrics only profile too). */
    fun keyOf(vararg texts: String): String = statedKey(texts)?.key.orEmpty()

    /**
     * The key for `{key}`, which holds the chart's shapes. Many pages give what sounds with the capo (F#m, capo 2, chart
     * in Em): the chords tell. A shape in parentheses counts if it fits them and isn't just the relative key; if not, a key
     * [capo] frets down that fits them.
     */
    fun shapesKey(capo: Int, chart: String, vararg texts: String): String {
        val page = statedKey(texts) ?: return ""
        val found = Chords.detectKey(chart)
        if (page.shape.isNotEmpty() && !sameField(page.shape, page.key) && sameField(page.shape, found)) return page.shape
        if (capo == 0) return page.key
        val shapes = Chords.transposeKey(page.key, -capo)
        return if (sameField(shapes, found)) shapes else page.key
    }

    // Em and G share a harmonic field
    private fun sameField(a: String, b: String): Boolean = b.isNotEmpty() && majorOf(a) == majorOf(b)
    private fun majorOf(key: String): String = if (key.endsWith("m")) Chords.transposeKey(key.dropLast(1), 3) else Chords.transposeKey(key, 0)

    /** BPM: what the page says ("120 BPM", "BPM: 120", "Tempo: 120"), or a line with it in the text itself; 0 = none. */
    fun bpmOf(page: String, text: String): Int {
        val fromText = bpmText.find(text)?.let { m -> m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } }
        val fromPage = pageBpm.find(page)?.let { m -> m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } }
        return listOf(fromPage.orEmpty(), fromText.orEmpty()).firstNotNullOfOrNull { it.toIntOrNull()?.takeIf { v -> v in Chords.MIN_TEMPO..Chords.MAX_TEMPO } } ?: 0
    }

    // "N bpm" ends its line: "Verse 88 bpm" is a tempo, "174BPM Wonderwall Remix" is another song's title
    private val pageBpm = Regex("""(?im)(\d{2,3})\s*bpm\s*$|\b(?:bpm|tempo)\s*[:=]?\s*(\d{2,3})\b""")
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
            parts += Chords.fromChordsOverLyrics(plain.filterNot { tabLine.matches(it) || keyText.matches(it.trim()) || (capoText.containsMatchIn(it) && it.length < 40) }.joinToString("\n"))
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
        val c = capoOf(capo, text)
        val k = shapesKey(c, body, key, text)
        if (c in 1..12) body = Chords.setDirective(body, "capo", c.toString())
        if (Chords.isChord(k)) body = Chords.setDirective(body, "key", k)
        return body
    }
}
