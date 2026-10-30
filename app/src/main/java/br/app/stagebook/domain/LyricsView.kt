package br.app.stagebook.domain

/**
 * Editing in the "lyrics only" profile: the editor shows the lyrics without chords ([view]) and each change goes back
 * into the song with the chords kept ([merge]). Someone who only sings can fix the lyrics without deleting anyone's
 * chords.
 *
 * Hidden lines (`{key}`, `{capo}`, chords-only lines) stay where they were; ChordPro sections and comments
 * (`{start_of_chorus}`, `{c: Solo}`) belong to the lyrics and show. An unchanged line keeps everything; a changed line
 * keeps the chords whose stretch before (or after) them didn't change; a new line gets no chord.
 */
object LyricsView {
    private val bracket = Regex("""\[([^\[\]]*)]""")

    /** The line without chords (the rest, spaces included, stays the same: positions match [chordsAt]). */
    private fun plain(line: String) = bracket.replace(line) { if (Chords.isChord(it.groupValues[1])) "" else it.value }

    // structure directives (sections, comments) belong to the lyrics and stay visible; the rest ({key}, {capo},
    // {define}…) is chords
    private val structure = Regex("""^\s*\{\s*(start_of_\w+|end_of_\w+|so[cvb]|eo[cvb]|comment|c|ci)\s*(:.*)?}\s*$""", RegexOption.IGNORE_CASE)

    private fun hidden(line: String) =
        (Chords.isDirective(line) && !structure.matches(line)) || (Chords.hasChords(line) && plain(line).isBlank())

    /** The line's chords and each one's position in the line without chords. */
    private fun chordsAt(line: String): List<Pair<Int, String>> {
        val out = ArrayList<Pair<Int, String>>()
        var removed = 0
        bracket.findAll(line).forEach { m ->
            if (!Chords.isChord(m.groupValues[1])) return@forEach
            out += (m.range.first - removed) to m.groupValues[1]
            removed += m.value.length
        }
        return out
    }

    fun view(text: String): String = text.split('\n').filterNot(::hidden).joinToString("\n") { plain(it) }

    fun merge(original: String, edited: String): String {
        val lines = original.split('\n')
        val visible = lines.indices.filterNot { hidden(lines[it]) }
        val old = visible.map { plain(lines[it]) }
        val new = edited.split('\n')
        val pairs = lcs(old, new)
        val out = ArrayList<String>(lines.size + 4)
        var nextLine = 0   // next line of the original song not written yet (for the hidden ones)
        fun hiddenUntil(line: Int) {
            for (k in nextLine until line) if (hidden(lines[k])) out += lines[k]
            nextLine = maxOf(nextLine, line)
        }
        var oi = 0
        var nj = 0
        for ((pi, pj) in pairs + (old.size to new.size)) {
            // changed stretch between two equal lines: the k-th old one becomes the k-th new one
            val gapOld = oi until pi
            val gapNew = nj until pj
            gapNew.forEachIndexed { k, j ->
                val o = gapOld.elementAtOrNull(k)
                if (o != null) {
                    hiddenUntil(visible[o])
                    nextLine = visible[o] + 1
                    out += reinsert(lines[visible[o]], new[j])
                } else {
                    out += new[j]
                }
            }
            // old lines deleted: the hidden ones before them stay
            gapOld.drop(gapNew.count()).forEach { o -> hiddenUntil(visible[o]); nextLine = visible[o] + 1 }
            if (pi < old.size) {
                hiddenUntil(visible[pi])
                out += lines[visible[pi]]
                nextLine = visible[pi] + 1
            }
            oi = pi + 1
            nj = pj + 1
        }
        hiddenUntil(lines.size)
        return out.joinToString("\n")
    }

    /** The new line with the old one's chords whose stretch before (or after) them is still the same. */
    private fun reinsert(oldLine: String, newText: String): String {
        val oldPlain = plain(oldLine)
        val keep = chordsAt(oldLine).mapNotNull { (at, chord) ->
            when {
                newText.startsWith(oldPlain.substring(0, at)) -> at to chord
                newText.endsWith(oldPlain.substring(at)) -> (newText.length - (oldPlain.length - at)) to chord
                else -> null
            }
        }
        val sb = StringBuilder(newText)
        keep.sortedByDescending { it.first }.forEach { (at, chord) -> sb.insert(at.coerceIn(0, sb.length), "[$chord]") }
        return sb.toString()
    }

    /** Pairs (old, new) of equal lines, in order (longest common subsequence). */
    private fun lcs(a: List<String>, b: List<String>): List<Pair<Int, Int>> {
        val t = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            t[i][j] = if (a[i] == b[j]) t[i + 1][j + 1] + 1 else maxOf(t[i + 1][j], t[i][j + 1])
        }
        val out = ArrayList<Pair<Int, Int>>()
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] == b[j] -> { out += i to j; i++; j++ }
                t[i + 1][j] >= t[i][j + 1] -> i++
                else -> j++
            }
        }
        return out
    }
}
