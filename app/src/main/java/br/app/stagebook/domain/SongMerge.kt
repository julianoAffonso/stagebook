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
        val t = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            t[i][j] = if (a[i] == b[j]) t[i + 1][j + 1] + 1 else maxOf(t[i + 1][j], t[i][j + 1])
        }
        val out = ArrayList<Change>()
        var i = 0
        var j = 0
        while (i < a.size || j < b.size) {
            when {
                i < a.size && j < b.size && a[i] == b[j] -> { i++; j++ }
                j < b.size && (i == a.size || t[i][j + 1] > t[i + 1][j]) -> { if (b[j].isNotBlank()) out += Change(Kind.ADDED, b[j]); j++ }
                else -> { if (a[i].isNotBlank()) out += Change(Kind.REMOVED, a[i]); i++ }
            }
        }
        return out
    }
}
