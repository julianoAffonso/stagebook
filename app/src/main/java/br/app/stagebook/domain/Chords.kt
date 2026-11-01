package br.app.stagebook.domain

/**
 * Chords in ChordPro (open standard, chordpro.org), the format Stagebook saves songs in: the chord goes in brackets
 * before the syllable (`[G]Hoje eu [D]vou`) and info in directives (`{key: G}`, `{capo: 2}`). The pure rules live here:
 * recognize a chord, convert a chart with chords on the line above, transpose, key with capo and strip the chords
 * ("lyrics only" profile).
 *
 * `[Refrão]` also uses brackets (a section title, see [Sections]): only what looks like a chord is a chord.
 */
object Chords {
    private val sharps = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val flats = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
    // keys written with flats (major and minor); the others with sharps
    private val flatKeys = setOf("F", "Bb", "Eb", "Ab", "Db", "Gb", "Cb", "Dm", "Gm", "Cm", "Fm", "Bbm", "Ebm", "Abm")

    // note, accidental, the rest of the chord (m, 7, maj7, sus4, add9, dim, aug, °, +, 7M, (9), (b5)…) and the bass
    // a slash before a number is an extension, as the chord charts write it (A7/4, C#7/9, G6/9, D7(2/4)); before a
    // note it's the bass (D/F#)
    private val chord = Regex("""([A-G])([#b♯♭]?)((?:maj|min|dim|aug|sus|add|m|M|°|º|\+|-|\d|\(|\)|#|b|♯|♭|,|/(?=\d))*)(?:/([A-G])([#b♯♭]?))?""")
    internal val bracket = Regex("""\[([^\[\]]*)]""")   // LyricsView reads the same brackets
    // the value can't hold "}": the lazy ".*?" between optional spaces backtracked for minutes on a long malformed line
    // (audit 01/11); the value is trimmed where it's read
    private val directive = Regex("""^\s*\{\s*([A-Za-z_][A-Za-z_-]*)\s*(?::([^}]*))?\}\s*$""")
    // marks that can be on a chord line without making it a lyrics line
    // built once: these ran per line (twice per line in the editor preview, on every keystroke; audit 30/10)
    private val SPACES = Regex("\\s+")
    private val WORDS = Regex("\\S+")
    private val DOUBLE_SPACES = Regex(" {2,}")
    private val chordLineExtras = Regex("""\||/|%|-+|\(?\d+\s*x\)?|\(?x\s*\d+\)?""", RegexOption.IGNORE_CASE)

    /** Is the whole text a chord? (`G`, `F#m7/E`, `Cm7(b5)`, `G7M`, `Bb`) */
    fun isChord(s: String): Boolean = chord.matchEntire(s.trim()) != null

    // ---- diretivas ----

    fun isDirective(line: String): Boolean = directiveIn(line) != null

    // a directive needs a "{": the other lines skip the regex (the editor reads the key, capo and tempo on every
    // keystroke, over every line; audit 01/11)
    private fun directiveIn(line: String): MatchResult? = if (line.indexOf('{') < 0) null else directive.matchEntire(line)

    /** A directive's value (`key`, `capo`…), or null. */
    fun directive(text: String, name: String): String? = text.lineSequence()
        .mapNotNull { directiveIn(it) }
        .firstOrNull { it.groupValues[1].equals(name, ignoreCase = true) }
        ?.groupValues?.get(2)?.trim()

    /** Saves (or removes, with an empty value) a directive; a new one goes at the start of the text. */
    fun setDirective(text: String, name: String, value: String): String {
        val lines = text.split('\n').toMutableList()
        val i = lines.indexOfFirst { directiveIn(it)?.groupValues?.get(1).equals(name, ignoreCase = true) }
        val line = "{$name: $value}"
        when {
            i >= 0 && value.isEmpty() -> lines.removeAt(i)
            i >= 0 -> lines[i] = line
            value.isNotEmpty() -> lines.add(0, line)
        }
        return lines.joinToString("\n")
    }

    private val meta = setOf("key", "capo", "tempo")

    /** Song BPM (ChordPro `{tempo: 120}`); 0 = none. */
    fun tempo(text: String): Int = directive(text, "tempo")?.filter(Char::isDigit)?.toIntOrNull()?.takeIf { it in MIN_TEMPO..MAX_TEMPO } ?: 0

    const val MIN_TEMPO = 20
    const val MAX_TEMPO = 300
    const val DEFAULT_TEMPO = 100   // metronome when the song has no BPM

    private fun isMeta(line: String) = directiveIn(line)?.groupValues?.get(1)?.lowercase() in meta

    /**
     * The lyrics without the key and capo lines (in the editor they live in the song details, not the lyrics field).
     */
    fun hideMeta(text: String): String = text.split('\n').filterNot(::isMeta).joinToString("\n")

    /** The edited lyrics (without key and capo) with [from]'s key and capo back at the start. */
    fun withMeta(edited: String, from: String): String = withMeta(edited, meta(from))

    /** The edited lyrics with the key, capo and BPM already read from the text before ([meta]). */
    fun withMeta(edited: String, from: SongMeta): String {
        var t = hideMeta(edited)
        from.tempo.takeIf { it > 0 }?.let { t = setDirective(t, "tempo", it.toString()) }
        from.capo.takeIf { it > 0 }?.let { t = setDirective(t, "capo", it.toString()) }
        from.key.takeIf { it.isNotEmpty() }?.let { t = setDirective(t, "key", it) }
        return t
    }

    fun key(text: String): String = directive(text, "key").orEmpty()

    /** Key, capo and BPM read in one pass over the lines (the editor reads them on every keystroke; audit 01/11). */
    fun meta(text: String): SongMeta {
        var key: String? = null
        var capo: String? = null
        var tempo: String? = null
        for (line in text.lineSequence()) {
            val m = directiveIn(line) ?: continue
            val value = m.groupValues[2].trim()
            when (m.groupValues[1].lowercase()) {
                "key" -> if (key == null) key = value
                "capo" -> if (capo == null) capo = value
                "tempo" -> if (tempo == null) tempo = value
            }
            if (key != null && capo != null && tempo != null) break
        }
        return SongMeta(
            key.orEmpty(),
            capo?.toIntOrNull()?.coerceIn(0, 12) ?: 0,
            tempo.orEmpty(),
            tempo?.filter(Char::isDigit)?.toIntOrNull()?.takeIf { it in MIN_TEMPO..MAX_TEMPO } ?: 0,
        )
    }

    // chords the user took off the stage chord list (ChordPro extension: x_ directives belong to apps)
    private const val HIDE = "x_hide_chords"

    fun hiddenChords(text: String): Set<String> =
        directive(text, HIDE).orEmpty().split(SPACES).filter { it.isNotEmpty() }.toSet()

    fun setHiddenChords(text: String, chords: Set<String>): String = setDirective(text, HIDE, chords.joinToString(" "))

    fun capo(text: String): Int = directive(text, "capo")?.toIntOrNull()?.coerceIn(0, 12) ?: 0

    // ---- chords on the line above → ChordPro ----

    /**
     * Chords-only line (and marks like `|`, `(2x)`), with at least one chord. Accepts a label first: `Intro: G D`,
     * `[Intro] G D`.
     */
    fun isChordLine(line: String): Boolean {
        val tokens = line.trim().split(SPACES).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return false
        val body = if (tokens.size > 1 && isLabel(tokens[0])) tokens.drop(1) else tokens
        return body.any(::isChord) && body.all { isChord(it) || chordLineExtras.matches(it) }
    }

    private fun isLabel(t: String) =
        (t.endsWith(":") && !isChord(t.dropLast(1))) || (t.startsWith("[") && t.endsWith("]") && !isChord(t.drop(1).dropLast(1)))

    /**
     * Converts a chart with chords on the line above (Cifra Club, pasted text) to ChordPro: each chord goes into the
     * lyrics on the line below, at the same column. A chord line with no lyrics below (intro, solo) becomes a
     * chords-only line. What's already ChordPro passes unchanged.
     */
    fun fromChordsOverLyrics(text: String): String {
        val lines = text.replace("\t", "    ").split('\n')
        val out = ArrayList<String>(lines.size)
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (!isChordLine(line)) { out += line; i++; continue }
            // "[Intro] G D" (section label in front): the title on one line and the chords on the next
            val first = line.trim().substringBefore(' ')
            if (first.startsWith("[") && Sections.detect(first) != null && line.trim().length > first.length) {
                out += first
                out += chordsOnly(line.trim().removePrefix(first))
                i++
                continue
            }
            val next = lines.getOrNull(i + 1)
            val lyric = next != null && next.isNotBlank() && !isChordLine(next) && !isDirective(next) && Sections.detect(next) == null
            out += if (lyric) merge(line, next!!) else chordsOnly(line)
            i += if (lyric) 2 else 1
        }
        return out.joinToString("\n")
    }

    private fun positions(chordLine: String): List<Pair<Int, String>> =
        WORDS.findAll(chordLine).map { it.range.first to it.value }.toList()

    private fun merge(chordLine: String, lyric: String): String {
        val sb = StringBuilder(lyric.trimEnd())
        positions(chordLine).reversed().forEach { (col, token) ->
            val mark = if (isChord(token)) "[$token]" else return@forEach
            if (col >= sb.length) {
                // chord after the end of the lyrics: stays at the end, separate
                while (sb.length < col) sb.append(' ')
                sb.append(mark)
            } else {
                sb.insert(col, mark)
            }
        }
        return sb.toString()
    }

    private fun chordsOnly(chordLine: String): String =
        positions(chordLine).joinToString(" ") { (_, t) -> if (isChord(t)) "[$t]" else t }

    // ---- transpor ----

    private fun index(note: String): Int {
        val n = note.replace('♯', '#').replace('♭', 'b')
        val i = sharps.indexOf(n).takeIf { it >= 0 } ?: flats.indexOf(n)
        return when {
            i >= 0 -> i
            n == "Cb" -> 11
            n == "Fb" -> 4
            n == "E#" -> 5
            n == "B#" -> 0
            else -> -1
        }
    }

    private fun name(i: Int, useFlats: Boolean) = (if (useFlats) flats else sharps)[((i % 12) + 12) % 12]

    /** The chord [semitones] half steps up (negative = down). [useFlats] null = the chord's own style. */
    private val PARENS = Regex("""\([^)]*\)""")
    private val SLASH_EXTENSION = Regex("""/(\d+M?)""")
    private val SIGN_BEFORE = Regex("""([b#♭♯])(\d+)""")
    private val SUS = Regex("""sus(2|4)?(?!\d)""")

    /**
     * A chord in the chart notation the app uses, the same as Cifra Club (user, 01/11): `7+` and `maj7` become `7M`,
     * a slash before a number becomes parentheses (`C#7/9` → `C#7(9)`; a slash inside them stays, `D7(2/4)`), `sus4`
     * and `sus` become `4` and `sus2` becomes `2`, and the sign goes after the number (`b13` → `13-`, `#9` → `9+`).
     * Root and bass stay; what isn't a chord passes unchanged.
     */
    fun normalizeChord(c: String): String {
        val m = chord.matchEntire(c.trim()) ?: return c
        val (root, acc, rest, bass, bassAcc) = m.destructured
        var r = rest.replace("maj7", "7M").replace("7+", "7M")
        r = SUS.replace(r) { if (it.groupValues[1] == "2") "2" else "4" }
        // signs: inside parentheses they just move after the number; outside, the alteration gets its own
        val inside = StringBuilder()
        var last = 0
        PARENS.findAll(r).forEach { p ->
            inside.append(outside(r.substring(last, p.range.first)))
            inside.append(SIGN_BEFORE.replace(p.value) { s -> s.groupValues[2] + sign(s.groupValues[1]) })
            last = p.range.last + 1
        }
        inside.append(outside(r.substring(last)))
        return root + acc + inside + if (bass.isNotEmpty()) "/$bass$bassAcc" else ""
    }

    private fun sign(s: String) = if (s == "b" || s == "♭") "-" else "+"

    private fun outside(part: String): String =
        SIGN_BEFORE.replace(SLASH_EXTENSION.replace(part) { "(" + it.groupValues[1] + ")" }) { "(" + it.groupValues[2] + sign(it.groupValues[1]) + ")" }

    /** Every chord in the ChordPro text in the app's notation ([normalizeChord]); the rest of the text stays. */
    fun normalizeChords(text: String): String =
        bracket.replace(text) { b -> if (isChord(b.groupValues[1])) "[" + normalizeChord(b.groupValues[1].trim()) + "]" else b.value }

    fun transposeChord(c: String, semitones: Int, useFlats: Boolean? = null): String {
        val m = chord.matchEntire(c.trim()) ?: return c
        val (root, acc, rest, bass, bassAcc) = m.destructured
        val flat = useFlats ?: (acc == "b" || acc == "♭")
        val r = index(root + acc).takeIf { it >= 0 } ?: return c
        val out = StringBuilder(name(r + semitones, flat)).append(rest)
        if (bass.isNotEmpty()) {
            val b = index(bass + bassAcc)
            out.append('/').append(if (b >= 0) name(b + semitones, flat) else bass + bassAcc)
        }
        return out.toString()
    }

    // the key names musicians use, half step by half step (user's request, 29/10): the editor's − and + move in this
    // order, and the chords follow the key signature (flats in flat keys)
    private val majorKeys = listOf("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
    private val minorKeys = listOf("Cm", "C#m", "Dm", "Ebm", "Em", "Fm", "F#m", "Gm", "G#m", "Am", "Bbm", "Bm")

    /** The chord's seventh: none, major (7M), minor (7, m7, m7(b5)) or diminished (°7). */
    enum class Seventh { NONE, MAJOR, MINOR, DIMINISHED }

    /**
     * Base triad (root 0–11 and quality) and the seventh, for the harmonic field; null if it isn't a chord. The seventh
     * counts: in G, Cmaj7 is the IV, but C7 isn't in the field (user's request, 29/10: ignoring the seventh gave wrong
     * information).
     */
    data class Triad(val root: Int, val minor: Boolean, val dim: Boolean, val seventh: Seventh = Seventh.NONE)

    fun triad(c: String): Triad? {
        val m = chord.matchEntire(c.trim()) ?: return null
        val root = index(m.groupValues[1] + m.groupValues[2]).takeIf { it >= 0 } ?: return null
        val n = ChordTheory.notes(c) ?: return null
        val q = quality(n)
        val seventh = when {
            11 in n.intervals -> Seventh.MAJOR
            10 in n.intervals -> Seventh.MINOR
            q == Quality.DIM && 9 in n.intervals -> Seventh.DIMINISHED   // in a diminished chord the 6th is the °7
            else -> Seventh.NONE
        }
        return Triad(root, q == Quality.MINOR, q == Quality.DIM, seventh)
    }

    /** Usual name of the key with root [root] (C, Db… / Cm, C#m…). */
    fun keyName(root: Int, minor: Boolean): String = (if (minor) minorKeys else majorKeys)[((root % 12) + 12) % 12]

    /** Name of note [pc] in key [key]'s signature (flats in flat keys). */
    fun noteIn(key: String, pc: Int): String = name(pc, key in flatKeys)

    /** The key [semitones] half steps up, with the usual name (D + 1 = Eb, F + 1 = F#, Am + 1 = Bbm). */
    fun transposeKey(key: String, semitones: Int): String {
        val m = chord.matchEntire(key.trim()) ?: return key
        val root = index(m.groupValues[1] + m.groupValues[2]).takeIf { it >= 0 } ?: return key
        val minor = quality(key.trim()) == Quality.MINOR
        val i = ((root + semitones) % 12 + 12) % 12
        return (if (minor) minorKeys else majorKeys)[i]
    }

    /** Transposes every chord in the song and the saved key (`{key}`), with flats in flat keys. */
    fun transpose(text: String, semitones: Int): String {
        if (semitones % 12 == 0) return text
        val oldKey = key(text)
        val newKey = if (oldKey.isNotEmpty()) transposeKey(oldKey, semitones) else ""
        // the new key decides flat vs sharp; with no key, each chord keeps its own style
        val useFlats = if (newKey.isNotEmpty()) newKey in flatKeys else null   // the key name is already the usual one
        val body = text.split('\n').joinToString("\n") { line ->
            if (isDirective(line)) line
            else bracket.replace(line) { b -> if (isChord(b.groupValues[1])) "[" + transposeChord(b.groupValues[1], semitones, useFlats) + "]" else b.value }
        }
        // the tablature moves too (T22)
        val tabbed = Tablature.shift(body, semitones)
        return if (newKey.isNotEmpty()) setDirective(tabbed, "key", newKey) else tabbed
    }

    /**
     * Moves the song [semitones] half steps (editor and stage): chords above the lyrics become ChordPro first and, with
     * no saved key, the one computed from the chords is saved first so the key moves with the chart.
     */
    fun transposeSong(text: String, semitones: Int): String {
        val base = fromChordsOverLyrics(text).let { t ->
            if (key(t).isNotEmpty()) t else detectKey(t).takeIf { it.isNotEmpty() }?.let { setDirective(t, "key", it) } ?: t
        }
        return transpose(base, semitones)
    }

    /**
     * Capo on fret [capo] (0 removes the directive): the chord diagrams stay the same, the sounding key changes. The
     * tablature stays in frets without a capo: the capo goes up, the frets go up with it (user's decision, 29/10).
     */
    fun withCapo(text: String, capo: Int): String {
        val now = capo.coerceIn(0, 12)
        val shifted = Tablature.shift(text, now - capo(text))
        return setDirective(shifted, "capo", now.takeIf { it > 0 }?.toString().orEmpty())
    }

    // ---- key from the chords (import without {key}) ----

    private enum class Quality { MAJOR, MINOR, DIM }

    // read from the chord's notes, the same reading as the diagram and the harmonic field (m7(5-) is diminished, sus
    // has no third and counts as major; user, 01/11: every chord feature follows one rule)
    private fun quality(chord: String): Quality = ChordTheory.notes(chord)?.let(::quality) ?: Quality.MAJOR

    private fun quality(n: ChordTheory.Notes): Quality = with(n.intervals) {
        when {
            3 in this && 6 in this && 7 !in this -> Quality.DIM
            3 in this && 4 !in this -> Quality.MINOR
            else -> Quality.MAJOR
        }
    }

    // harmonic field degrees: major I ii iii IV V vi vii°; minor (natural, with the harmonic's major V) i ii° III iv v
    // V VI VII
    private val majorField = listOf(0 to Quality.MAJOR, 2 to Quality.MINOR, 4 to Quality.MINOR, 5 to Quality.MAJOR, 7 to Quality.MAJOR, 9 to Quality.MINOR, 11 to Quality.DIM)
    private val minorField = listOf(0 to Quality.MINOR, 2 to Quality.DIM, 3 to Quality.MAJOR, 5 to Quality.MINOR, 7 to Quality.MINOR, 7 to Quality.MAJOR, 8 to Quality.MAJOR, 10 to Quality.MAJOR)

    /**
     * The likely key from the chords (e.g. "G", "Em"); empty with no chords. Each key (12 major and 12 minor) gets a
     * point per chord in its harmonic field; the first and last chords count more when they're the tonic. On a tie:
     * major.
     */
    fun detectKey(text: String): String {
        val chords = bracket.findAll(fromChordsOverLyrics(text)).mapNotNull { chord.matchEntire(it.groupValues[1].trim()) }
            .mapNotNull { m -> index(m.groupValues[1] + m.groupValues[2]).takeIf { it >= 0 }?.let { it to quality(m.value) } }
            .toList()
        if (chords.isEmpty()) return ""
        var best = "" ; var bestScore = -1.0
        for (minor in listOf(false, true)) for (tonic in 0 until 12) {
            val field = if (minor) minorField else majorField
            val tonicChord = tonic to if (minor) Quality.MINOR else Quality.MAJOR
            var score = chords.count { (r, q) -> field.any { (deg, fq) -> (tonic + deg) % 12 == r && fq == q } }.toDouble()
            if (chords.first() == tonicChord) score += 2.0
            if (chords.last() == tonicChord) score += 1.5
            if (score > bestScore) {
                bestScore = score
                best = (if (minor) minorKeys else majorKeys)[tonic]   // the key's usual name
            }
        }
        return best
    }

    /** No {key}: saves the key computed from the chords (on import and on save in the editor; a key already written wins). */
    fun withDetectedKey(text: String): String =
        if (key(text).isNotEmpty()) text else detectKey(text).takeIf { it.isNotEmpty() }?.let { setDirective(text, "key", it) } ?: text

    /** Key that sounds with the capo: the shapes stay, the sound goes up [capo] frets (G with capo 2 sounds A). */
    fun soundingKey(key: String, capo: Int): String = if (key.isEmpty() || capo == 0) key else transposeChord(key, capo)

    // ---- "lyrics only" profile ----

    /** The lyrics without chords: removes the `[C]`s, chords-only lines and directives. Section titles stay. */
    fun stripChords(text: String): String = text.split('\n').mapNotNull { line ->
        if (isDirective(line)) return@mapNotNull null
        val hadChord = bracket.findAll(line).any { isChord(it.groupValues[1]) }
        val clean = bracket.replace(line) { b -> if (isChord(b.groupValues[1])) "" else b.value }
        if (hadChord && clean.isBlank()) null else clean.replace(DOUBLE_SPACES, " ").trimEnd()
    }.joinToString("\n")

    /** Does the song have any chord? */
    fun hasChords(text: String): Boolean = bracket.findAll(text).any { isChord(it.groupValues[1]) }
}

/** A song's key, capo and BPM ([Chords.meta]); [tempoText] is the directive as written (the field shows it). */
data class SongMeta(val key: String = "", val capo: Int = 0, val tempoText: String = "", val tempo: Int = 0)
