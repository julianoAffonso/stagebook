package br.app.stagebook.domain

/**
 * Tablature inside the chart (T22). Saved as ChordPro `{start_of_tab}`…`{end_of_tab}`; loose lines that look like tab
 * (`e|---0---3---|`) count too. [shift] moves frets up/down keeping the columns aligned: transposing the key and
 * changing the capo move them (user's decision, 29/10: the tab stays in frets without a capo). A fret that would go
 * negative goes up an octave.
 */
object Tablature {
    const val START = "{start_of_tab}"
    const val END = "{end_of_tab}"

    private val startDirective = Regex("""^\s*\{\s*(start_of_tab|sot)\s*(:.*)?\}\s*$""", RegexOption.IGNORE_CASE)
    private val endDirective = Regex("""^\s*\{\s*(end_of_tab|eot)\s*\}\s*$""", RegexOption.IGNORE_CASE)
    // string name (optional) + bar, then only dashes, digits and technique marks (h p b r / \ ~ x)
    private val BLANK_LINES = Regex("\n{3,}")
    private val tabLine = Regex("""^\s*([A-Ga-g][#b]?\s*)?[|:][-0-9|hpbrxHPBRX/\\~().*\s]+$""")

    fun isStart(line: String) = startDirective.matches(line)
    fun isEnd(line: String) = endDirective.matches(line)

    /** A line that looks like tablature (needs dashes: "e|---0---|"). */
    fun isTabLine(line: String): Boolean = tabLine.matches(line) && line.count { it == '-' } >= 3

    /** For each line of the text: whether it's tablature (inside the block or looking like tab). */
    fun tabLines(text: String): List<Boolean> {
        var inBlock = false
        return text.split('\n').map { line ->
            when {
                isStart(line) -> { inBlock = true; false }
                isEnd(line) -> { inBlock = false; false }
                else -> inBlock || isTabLine(line)
            }
        }
    }

    fun hasTab(text: String): Boolean = tabLines(text).any { it }

    /** Moves up ([semitones] > 0) or down the frets of every tablature in the text, without moving the columns. */
    fun shift(text: String, semitones: Int): String {
        if (semitones == 0) return text
        val lines = text.split('\n')
        val flags = tabLines(text)
        val out = lines.toMutableList()
        // each block of tab lines moves together, an octave up or down when it would leave the neck: a fret under 0 sent
        // only that note up 12 and −1 then +1 turned the open strings into 12 (device test B-15). Now the whole block
        // goes up when any fret would be under 0, and down when all would be at 12 or more, so the shape stays and a
        // step down and back up gives the same tab
        var i = 0
        while (i < lines.size) {
            if (!flags[i]) { i++; continue }
            var end = i
            while (end < lines.size && flags[end]) end++
            val lowest = (i until end).flatMap { frets(lines[it]) }.minOrNull()
            val octave = when {
                lowest == null -> 0
                lowest + semitones < 0 -> 12
                lowest + semitones >= 12 -> -12
                else -> 0
            }
            val shifted = shiftBlock(lines.subList(i, end), semitones + octave)
            for (k in i until end) out[k] = shifted[k - i]
            i = end
        }
        return out.joinToString("\n")
    }

    /**
     * The strings of one tab block moved together, column by column: where a fret grows (9 → 10) every string gets the
     * column, where it shrinks every string loses it, so the strings stay aligned and a step down and back up gives
     * the same text. Moving each string on its own ate or gave back dashes and the strings drifted apart (B-15).
     */
    private fun shiftBlock(block: List<String>, semitones: Int): List<String> {
        val outs = block.map { StringBuilder() }
        val starts = block.map { l -> l.indexOfFirst { it == '|' || it == ':' }.coerceAtLeast(0) }
        val from = starts.maxOrNull() ?: 0
        block.forEachIndexed { k, l -> outs[k].append(l.substring(0, minOf(from, l.length))) }
        val width = block.maxOf { it.length }
        fun fretAt(l: String, c: Int): Int =   // length of the fret starting at c (1 or 2 digits), 0 if none
            if (c < l.length && l[c].isDigit() && (c == 0 || !l[c - 1].isDigit())) {
                var e = c; while (e < l.length && l[e].isDigit()) e++
                (e - c).takeIf { it <= MAX_FRET_DIGITS } ?: 0
            } else 0
        var c = from
        while (c < width) {
            val spans = block.map { fretAt(it, c) }
            val span = spans.maxOrNull() ?: 0
            if (span == 0) { block.forEachIndexed { k, l -> if (c < l.length) outs[k].append(l[c]) }; c++; continue }
            val news = block.mapIndexed { k, l -> if (spans[k] > 0) (l.substring(c, c + spans[k]).toInt() + semitones).coerceAtLeast(0).toString() else null }
            val need = news.maxOf { it?.length ?: 0 }
            // what the other strings have in these columns may only be dropped if it is dashes
            val canShrink = block.indices.all { k -> spans[k] > 0 || block[k].substring(minOf(c, block[k].length), minOf(c + span, block[k].length)).all { it == '-' } }
            val outWidth = if (need < span && !canShrink) span else need
            block.forEachIndexed { k, l ->
                val piece = news[k] ?: l.substring(minOf(c, l.length), minOf(c + span, l.length))
                val cell = if (piece.length > outWidth) piece.take(outWidth) else piece + "-".repeat(outWidth - piece.length)
                if (c < l.length) outs[k].append(cell)
            }
            c += span
        }
        return outs.map { it.toString() }
    }

    /** The frets in a tab line (1 or 2 digits after the string name), as [shiftLine] reads them. */
    private fun frets(line: String): List<Int> {
        val bar = line.indexOfFirst { it == '|' || it == ':' }.coerceAtLeast(0)
        return Regex("""\d+""").findAll(line.substring(bar)).map { it.value }.filter { it.length <= MAX_FRET_DIGITS }.map { it.toInt() }.toList()
    }

    private const val MAX_FRET_DIGITS = 2

    /** A tab line with the frets shifted; the string name at the start (e, B, G…) isn't a fret. */
    fun shiftLine(line: String, semitones: Int): String {
        val bar = line.indexOfFirst { it == '|' || it == ':' }.coerceAtLeast(0)
        val out = StringBuilder(line.substring(0, bar))
        var i = bar
        while (i < line.length) {
            val c = line[i]
            if (!c.isDigit()) { out.append(c); i++; continue }
            var j = i
            while (j < line.length && line[j].isDigit()) j++
            // a fret has 1 or 2 digits; a longer run (pasted junk, a phone number) stays as it is instead of crashing
            // the app when transposing (audit 30/10)
            val digits = line.substring(i, j)
            if (digits.length > MAX_FRET_DIGITS) { out.append(digits); i = j; continue }
            var fret = digits.toInt() + semitones
            while (fret < 0) fret += 12
            val now = fret.toString()
            var width = j - i
            out.append(now)
            // a fret with more digits eats the dashes after it; with fewer, gives dashes back (the columns don't move)
            // never the dash that separates it from the next fret: "3-3" two up an octave became "1414" (B-15)
            while (now.length > width && j < line.length && line[j] == '-' && !(j + 1 < line.length && line[j + 1].isDigit())) { j++; width++ }
            repeat(width - now.length) { out.append('-') }
            i = j
        }
        return out.toString()
    }

    /** Wraps loose tab lines between `{start_of_tab}` and `{end_of_tab}` (what came from the import). */
    fun wrap(text: String): String {
        val lines = text.split('\n')
        val flags = lines.map(::isTabLine)
        val out = ArrayList<String>()
        lines.forEachIndexed { i, line ->
            val prev = i > 0 && flags[i - 1]
            val next = i < lines.lastIndex && flags[i + 1]
            if (flags[i] && !prev) out += START
            out += line
            if (flags[i] && !next) out += END
        }
        return out.joinToString("\n")
    }

    /**
     * Removes all tablature (import without the tab), with what only made sense beside it: a part that is only
     * tablature ("[Tab - Intro]") and its captions, and a part left with nothing ("[Solo]" that was all tab). A title
     * that came alone ("[Refrão]" as a cue to sing it again) stays.
     */
    fun strip(text: String): String {
        val lines = text.split('\n')
        val flags = tabLines(text)
        val hadTab = flags.any { it }
        val out = ArrayList<String?>(lines.size)
        var inTab = false
        // chords after a blank line inside the tab part: they may be the first lyrics line's chords, kept if lyrics follow
        val held = ArrayList<String>()
        // the open part: where its title is in [out], what stayed in it and what went
        var titleAt = -1
        var keptIn = 0
        var droppedIn = 0
        fun closePart() { if (titleAt >= 0 && keptIn == 0 && droppedIn > 0) out[titleAt] = null }
        for ((i, line) in lines.withIndex()) {
            if (flags[i] || isStart(line) || isEnd(line)) { droppedIn++; continue }
            val trimmed = line.trim()
            val title = PART_TITLE.matchEntire(trimmed)?.groupValues?.get(1)?.trim()?.takeIf { !Chords.isChord(it) }
            if (title != null) {
                held.clear()
                // a tab part ("[Tab - Intro]", "[Tab - Solo]") goes whole and belongs to the part above it (B-04)
                if (TAB_PART.containsMatchIn(title)) { inTab = true; droppedIn++; continue }
                closePart()
                inTab = false
                out += line; titleAt = out.lastIndex; keptIn = 0; droppedIn = 0
                continue
            }
            if (inTab) {
                // the tab part ends at the first line of lyrics: Cifra Club often has no other title after "[Tab - Intro]",
                // and the whole song went away with it ("Under Pressure" came out as "not a song", S26 retest)
                when {
                    line.isBlank() -> { held.clear(); held += ""; continue }
                    Chords.isChordLine(line) -> { if (held.isNotEmpty()) held += line else droppedIn++; continue }
                    isTabPiece(trimmed) || flags.getOrElse(i + 1) { false } -> { droppedIn++; continue }
                }
                inTab = false
                val chords = held.drop(1)
                out += chords; keptIn += chords.size
                held.clear()
            } else if (hadTab && RIFF_CUE.matches(trimmed)) { droppedIn++; continue }   // "(Riff Principal)": points at a tab that is gone
            out += line
            if (line.isNotBlank()) keptIn++
        }
        closePart()
        return out.filterNotNull().joinToString("\n").replace(BLANK_LINES, "\n\n")
    }

    // what a tab part has besides the strings: "Parte 1 de 2", "(3x)", "Riff", "(Guitarra Base)", a line of marks ("  ~")
    private fun isTabPiece(line: String): Boolean =
        TAB_PIECE.matches(line) || (line.startsWith("(") && line.endsWith(")")) || line.none(Char::isLetter)

    private val PART_TITLE = Regex("""\[([^\[\]]+)]""")
    private val TAB_PART = Regex("""^(tab|tabs|tablatura|tablature)\b""", RegexOption.IGNORE_CASE)
    private val TAB_PIECE = Regex("""(parte|part)\s+\d+\s+(de|of)\s+\d+|\(?\d+x\)?|riff""", RegexOption.IGNORE_CASE)
    private val RIFF_CUE = Regex("""\([^()]*\briff\b[^()]*\)""", RegexOption.IGNORE_CASE)
}
