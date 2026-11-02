package br.app.stagebook.domain

/**
 * Same song, different versions (came from the band or through the 3 words): "the singer's lyrics, the player's
 * chords". The receiver chooses song by song; accepting theirs never deletes the receiver's chords.
 */
object SongMerge {
    enum class Kind { REMOVED, ADDED }

    /** A line that changes in the lyrics: mine leaves ([Kind.REMOVED]) or theirs comes in ([Kind.ADDED]). */
    data class Change(val kind: Kind, val text: String)

    private fun norm(text: String) = text.replace("\r", "").split('\n').map { it.trimEnd() }.dropLastWhile { it.isEmpty() }

    /** The same version (trailing spaces and a trailing blank line don't count). */
    fun same(a: String, b: String) = norm(a) == norm(b)

    /**
     * The received version accepted: if it came with lyrics only and mine has chords, the new lyrics come in **with my
     * chords** (in the stretches that didn't change); otherwise, theirs whole.
     */
    fun accept(mine: String, incoming: String): String =
        if (Chords.hasChords(mine) && !Chords.hasChords(incoming)) LyricsView.merge(mine, LyricsView.view(incoming)) else incoming

    /** The lyrics lines (without chords) that change from mine to theirs, in order. Empty = only the chords change. */
    fun changes(mine: String, incoming: String): List<Change> {
        val a = LyricsView.view(mine).split('\n').map { it.trimEnd() }
        val b = LyricsView.view(incoming).split('\n').map { it.trimEnd() }
        // the equal lines (LineDiff); between two of them, what left mine and what came in theirs
        val out = ArrayList<Change>()
        var i = 0
        var j = 0
        for ((pi, pj) in LineDiff.pairs(a, b) + (a.size to b.size)) {
            while (i < pi) { if (a[i].isNotBlank()) out += Change(Kind.REMOVED, a[i]); i++ }
            while (j < pj) { if (b[j].isNotBlank()) out += Change(Kind.ADDED, b[j]); j++ }
            i++; j++
        }
        return out
    }
}
