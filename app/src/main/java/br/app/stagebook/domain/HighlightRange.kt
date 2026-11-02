package br.app.stagebook.domain

/**
 * The stage's highlighter (user, 02/11: highlighting a whole line painted words they didn't want): the finger runs over
 * some words of a line as shown on stage, and the saved line gets `==` around those words. The stage line differs from
 * the saved one (no `[chords]`, no `==` marks, spaces added so chords don't collide), so the shown letters are matched
 * to the saved ones by their non-space letters, in order.
 */
object HighlightRange {
    private const val MARK = "=="
    private val chord = Regex("""\[([^\[\]]*)]""")

    /**
     * [saved] with the words from shown column [from] to [to] (either order, inclusive) highlighted, or with their
     * highlight taken off if they touch one. [shown]: the line (or the piece of it) as drawn on stage. Unchanged when
     * the shown text can't be found in the saved line.
     */
    fun toggle(saved: String, shown: String, from: Int, to: Int): String {
        val visible = visibleLetters(saved)                       // saved index of each letter the stage shows
        val shownLetters = shown.indices.filter { !shown[it].isWhitespace() }
        if (shownLetters.isEmpty() || visible.isEmpty()) return saved
        val shownText = shownLetters.joinToString("") { shown[it].toString() }
        val visibleText = visible.joinToString("") { saved[it].toString() }
        val offset = visibleText.indexOf(shownText).takeIf { it >= 0 } ?: return saved
        // a shown column (maybe a space) to the nearest letter after it, or the last one
        fun letterAt(col: Int): Int {
            val k = shownLetters.indexOfFirst { it >= col }.let { if (it < 0) shownLetters.lastIndex else it }
            return visible[offset + k]
        }
        var start = letterAt(minOf(from, to))
        var end = letterAt(maxOf(from, to))
        // out to whole words: neighbouring letters with only chords or marks between them are the same word
        var si = visible.indexOf(start)
        while (si > 0 && contiguous(saved, visible[si - 1], visible[si])) si--
        var ei = visible.indexOf(end)
        while (ei < visible.lastIndex && contiguous(saved, visible[ei], visible[ei + 1])) ei++
        start = visible[si]
        end = visible[ei] + 1
        // touching a highlight: take it off (the marks of every highlight the range touches)
        val marks = marks(saved)
        val touched = marks.indices.step(2).filter { k -> marks[k] < end && marks[k + 1] + MARK.length > start }
        if (touched.isNotEmpty()) {
            var out = saved
            for (k in touched.reversed()) {
                out = out.removeRange(marks[k + 1], marks[k + 1] + MARK.length).removeRange(marks[k], marks[k] + MARK.length)
            }
            return out
        }
        return saved.substring(0, start) + MARK + saved.substring(start, end) + MARK + saved.substring(end)
    }

    // the saved letters between two visible ones are only chords or marks (the same word on stage), no space
    private fun contiguous(saved: String, a: Int, b: Int): Boolean {
        if (saved[a].isWhitespace() || saved[b].isWhitespace()) return false
        val between = saved.substring(a + 1, b)
        return between.isEmpty() || hidden(between)
    }

    private fun hidden(s: String) = s.replace(chord, "").replace(MARK, "").isEmpty()

    /** Index in [saved] of each character the stage shows, in order (chords in brackets and `==` marks left out). */
    private fun visibleLetters(saved: String): List<Int> {
        val out = ArrayList<Int>()
        var i = 0
        while (i < saved.length) {
            val m = chord.matchAt(saved, i)
            if (m != null && Chords.isChord(m.groupValues[1])) { i = m.range.last + 1; continue }
            if (saved.startsWith(MARK, i)) { i += MARK.length; continue }
            if (!saved[i].isWhitespace()) out += i
            i++
        }
        return out
    }

    private fun marks(line: String): List<Int> {
        val at = ArrayList<Int>()
        var i = line.indexOf(MARK)
        while (i >= 0) { at += i; i = line.indexOf(MARK, i + MARK.length) }
        return at.take(at.size / 2 * 2)
    }
}
