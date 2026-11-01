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
        val flags = tabLines(text)
        return text.split('\n').mapIndexed { i, line -> if (flags[i]) shiftLine(line, semitones) else line }.joinToString("\n")
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
            while (now.length > width && j < line.length && line[j] == '-') { j++; width++ }
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

    /** Removes all tablature (import without the tab). */
    fun strip(text: String): String {
        val flags = tabLines(text)
        return text.split('\n').filterIndexed { i, line -> !flags[i] && !isStart(line) && !isEnd(line) }.joinToString("\n")
            .replace(BLANK_LINES, "\n\n")
    }
}
