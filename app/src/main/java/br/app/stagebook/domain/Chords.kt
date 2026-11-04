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

    internal val bracket = Regex("""\[([^\[\]]*)]""")   // LyricsView reads the same brackets
    // the value can't hold "}": the lazy ".*?" between optional spaces backtracked for minutes on a long malformed line
    // (audit 01/11); the value is trimmed where it's read
    private val directive = Regex("""^\s*\{\s*([A-Za-z_][A-Za-z_-]*)\s*(?::([^}]*))?\}\s*$""")
    // marks that can be on a chord line without making it a lyrics line
    // built once: these ran per line (twice per line in the editor preview, on every keystroke; audit 30/10)
    private val SPACES = Regex("\\s+")
    private val WORDS = Regex("\\S+")
    private val DOUBLE_SPACES = Regex(" {2,}")
    // also a guitar shape written after the chord, one fret per string ("B/F#  224442", user 03/11)
    private val chordLineExtras = Regex("""\||/|%|-+|\(?\d+\s*x\)?|\(?x\s*\d+\)?|\(?(?:$SHAPE)\)?""", RegexOption.IGNORE_CASE)

    /**
     * A guitar shape note after a chord: one fret per string, low to high, x = not played (`224442`, `x02220`), or with
     * dashes when a fret has two digits (`10-10-12-12-12-10`, what a transposed shape can become).
     */
    private const val SHAPE = "[0-9x]{6}|(?:[0-9x]{1,2}-){5}[0-9x]{1,2}"
    private val shapeToken = Regex("""\(?($SHAPE)\)?""", RegexOption.IGNORE_CASE)
    private val shapeHint = Regex("""^\s*(\S+)\s+\(?($SHAPE)\)?\s*$""", RegexOption.IGNORE_CASE)

    private fun frets(shape: String): List<Int> =
        (if ('-' in shape) shape.split('-') else shape.map { it.toString() }).map { it.toIntOrNull() ?: -1 }   // "x", and "xx" or "1x" that the pattern lets through

    private fun shapeText(frets: List<Int>): String {
        val parts = frets.map { if (it < 0) "x" else it.toString() }
        return if (parts.all { it.length == 1 }) parts.joinToString("") else parts.joinToString("-")
    }

    /** The guitar frets a line like `B/F#  224442` (or `[B/F#] 224442`) gives for [chord] (-1 = not played), if any. */
    fun shapeHint(text: String, chord: String): List<Int>? = shapeHints(text)[chord]

    // the song's fret notes by chord, read once per text: the chord list asked for each of its ~20 chords, and each ask
    // scanned the whole song (audit 03/11). One entry: the stage asks about the song on screen
    @Volatile private var hintsOf: Pair<String, Map<String, List<Int>>>? = null

    private fun shapeHints(text: String): Map<String, List<Int>> {
        hintsOf?.let { (t, m) -> if (t === text || t == text) return m }
        val m = HashMap<String, List<Int>>()
        for (line in text.lineSequence()) {
            if (!line.contains(' ')) continue
            val h = shapeHint.matchEntire(line.replace("[", "").replace("]", "")) ?: continue
            m.putIfAbsent(h.groupValues[1], frets(h.groupValues[2]))
        }
        hintsOf = text to m
        return m
    }

    /** A chords-only line written without brackets (`B/F#  224442`) with its chords in brackets, as ChordPro keeps them. */
    fun bracketChordLine(line: String): String =
        if (line.contains('[') || !isChordLine(line)) line else line.replace(WORDS) { if (isChord(it.value)) "[${it.value}]" else it.value }

    /**
     * A shape note moved with its chord ([transpose]): every played string [semitones] frets up, an octave down when
     * it would go past the 12th fret and every fretted string allows it, so it still plays the transposed chord (audit
     * 03/11: the name moved and the frets stayed, so the diagram drew the old chord under the new name).
     */
    private fun shiftShapes(line: String, semitones: Int): String {
        val m = shapeHint.matchEntire(line.replace("[", "").replace("]", "")) ?: return line
        val n = ((semitones % 12) + 12) % 12
        var moved = frets(m.groupValues[2]).map { if (it < 0) it else it + n }
        if (moved.any { it > 12 } && moved.all { it < 0 || it >= 12 }) moved = moved.map { if (it < 0) it else it - 12 }
        return shapeToken.replace(line) { shapeText(moved).let { t -> if (it.value.startsWith("(")) "($t)" else t } }
    }

    /** Is the whole text a chord? (`G`, `F#m7/E`, `Cm7(b5)`, `G7M`, `Bb`) */
    fun isChord(s: String): Boolean = ChordName.parse(s) != null

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
    /** A mark that goes with chords on a chords-only line: `|`, `/`, `%`, `-`, `2x`, `(x2)`. */
    fun isChordLineMark(token: String) = chordLineExtras.matches(token)

    fun isChordLine(line: String): Boolean {
        val tokens = line.trim().split(SPACES).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return false
        val body = if (tokens.size > 1 && isLabel(tokens[0])) tokens.drop(1) else tokens
        // the "all" first: a lyric line stops at its first word that isn't a chord (it ran on every line of the stage
        // and the editor preview, and "any" parsed every word first; audit 03/11)
        return body.all { isChord(it) || chordLineExtras.matches(it) } && body.any(::isChord)
    }

    /** A label in front of chords on a chords-only line: `Intro:` or `[Solo]`. */
    fun isChordLineLabel(t: String) = isLabel(t)

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
            // a chord with its frets ("B/F#  224442") is a note of its own, never put into the lyric below (merging it
            // dropped the frets from the song; audit 03/11)
            val lyric = next != null && next.isNotBlank() && !isChordLine(next) && !isDirective(next) && Sections.detect(next) == null &&
                positions(line).none { (_, t) -> shapeToken.matches(t) }
            out += if (lyric) merge(line, next!!) else chordsOnly(line)
            i += if (lyric) 2 else 1
        }
        return out.joinToString("\n")
    }

    private fun positions(chordLine: String): List<Pair<Int, String>> =
        WORDS.findAll(chordLine).map { it.range.first to it.value }.toList()

    private fun merge(chordLine: String, lyric: String): String {
        val sb = StringBuilder(lyric.trimEnd())
        // marks like "2x" can't sit inside the lyric: they go after it, so they aren't lost (audit 03/11)
        val notes = positions(chordLine).map { it.second }.filter { !isChord(it) && it.any(Char::isLetterOrDigit) }
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
        if (notes.isNotEmpty()) sb.append("   ").append(notes.joinToString(" "))
        return sb.toString()
    }

    private fun chordsOnly(chordLine: String): String =
        positions(chordLine).joinToString(" ") { (_, t) -> if (isChord(t)) "[$t]" else t }

    // ---- transpor ----

    private fun name(i: Int, useFlats: Boolean) = (if (useFlats) flats else sharps)[((i % 12) + 12) % 12]

    /**
     * The chord [semitones] half steps up (negative = down). [useFlats] null = the chord's own style. With the new
     * [key], each note is spelled by its degree in that key (see [spell]).
     */
    fun transposeChord(c: String, semitones: Int, useFlats: Boolean? = null, key: String = ""): String {
        val chord = ChordName.parse(c) ?: return c
        val flat = useFlats ?: (chord.accidental == "b" || chord.accidental == "♭")
        val note = { pc: Int -> if (key.isNotEmpty()) spell(pc, key) ?: name(pc, flat) else name(pc, flat) }
        val out = StringBuilder(note(chord.rootPc + semitones)).append(chord.suffix)
        if (chord.bass.isNotEmpty()) out.append('/').append(note(chord.bassPc + semitones))
        return out.append(chord.mark).toString()
    }

    private val letters = "CDEFGAB"
    private val letterPc = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    // the degree (letter steps from the tonic) of each half step above it: ♭2, 2, ♭3, 3, 4, ♯4, 5, ♭6, 6, ♭7, 7
    private val degreeOf = intArrayOf(0, 1, 1, 2, 2, 3, 3, 4, 5, 5, 6, 6)

    /**
     * Note [pc] spelled by its degree in [key]: in Dm the major third is F# (not Gb), in C the ♭7 is Bb. The spelling
     * depends only on the note's place in the key, so a half step up and back down gives the chart back as it was
     * (F#7(13) in Dm came back as Gb7(13), device test B-06). Null when it would need E#, B#, Cb, Fb or a double
     * accidental: the caller falls back to the key's flats or sharps.
     */
    internal fun spell(pc: Int, key: String): String? {
        val tonic = ChordName.parse(key) ?: return null
        val letter = letters.indexOf(tonic.root.first().uppercaseChar()).takeIf { it >= 0 } ?: return null
        val interval = ((pc - tonic.rootPc) % 12 + 12) % 12
        // the half step between the 4th and the 5th: ♭5 in flat keys (Ab in Dm), ♯4 in the others (F# in C)
        val degree = if (interval == 6 && key in flatKeys) 4 else degreeOf[interval]
        val target = (letter + degree) % 7
        val diff = ((pc - letterPc[target]) % 12 + 12 + 6) % 12 - 6
        val out = letters[target] + when (diff) { 0 -> ""; 1 -> "#"; -1 -> "b"; else -> return null }
        return out.takeUnless { it in setOf("E#", "B#", "Cb", "Fb") }
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
        val n = ChordName.parse(c)?.notes ?: return null
        val root = n.root
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
        val n = ChordName.parse(key)?.notes ?: return key
        val minor = quality(n) == Quality.MINOR
        val i = ((n.root + semitones) % 12 + 12) % 12
        return (if (minor) minorKeys else majorKeys)[i]
    }

    /** Transposes every chord in the song and the saved key (`{key}`), with flats in flat keys. */
    fun transpose(text: String, semitones: Int): String {
        if (semitones % 12 == 0) return text
        val oldKey = key(text)
        val newKey = if (oldKey.isNotEmpty()) transposeKey(oldKey, semitones) else ""
        // the new key decides flat vs sharp; with no key, each chord keeps its own style
        val useFlats = if (newKey.isNotEmpty()) newKey in flatKeys else null   // the key name is already the usual one
        val body = text.split('\n').joinToString("\n") { written ->
            // a chords-only line written without brackets moves too: with the keyboard's capo it showed in the old key,
            // colored as chords (audit 03/11)
            val line = bracketChordLine(written)
            if (isDirective(line)) line
            else shiftShapes(bracket.replace(line) { b -> if (isChord(b.groupValues[1])) "[" + transposeChord(b.groupValues[1], semitones, useFlats, newKey) + "]" else b.value }, semitones)
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
            // a key that can't be read ("C major" typed by hand) would stay behind while the chords move
            if (ChordName.parse(key(t)) != null) t else detectKey(t).takeIf { it.isNotEmpty() }?.let { setDirective(t, "key", it) } ?: t
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
        val chords = bracket.findAll(fromChordsOverLyrics(text)).mapNotNull { ChordName.parse(it.groupValues[1])?.notes }
            .map { n -> n.root to quality(n) }
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
    fun soundingKey(key: String, capo: Int): String = if (key.isEmpty() || capo == 0) key else transposeKey(key, capo)

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
