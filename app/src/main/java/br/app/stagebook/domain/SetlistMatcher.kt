package br.app.stagebook.domain

import br.app.stagebook.data.model.Song

/** Result of each QR song against this device's library. */
sealed interface QrMatch {
    /** Same title (and artist, when more than one has the same title). */
    data class Found(val song: Song) : QrMatch
    /** Nothing with the same title, or equal titles with no way to break the tie: the user picks or skips. */
    data class Similar(val candidates: List<Song>) : QrMatch
    data object Missing : QrMatch
}

/**
 * Matches each QR song with the library by title key (the same rule as the library); the artist breaks ties between
 * equal titles ("It's My Life" by Bon Jovi and by Talk Talk). With no equal title, suggests similar ones, and never
 * picks a different song on its own.
 */
object SetlistMatcher {
    private const val SIMILAR_MIN = 0.75
    private const val MAX_CANDIDATES = 3

    fun match(entry: QrPayload.Entry, library: List<Song>): QrMatch {
        val kt = TextKey.of(entry.title)
        val keys = TitleKeys.of(library)   // the library's keys once, not again for every entry
        val sameTitle = keys.byKey[kt].orEmpty()
        if (sameTitle.size == 1) return QrMatch.Found(sameTitle[0])
        if (sameTitle.size > 1) {
            val byArtist = sameTitle.filter { sameArtist(it.author, entry.artist) }
            return if (byArtist.size == 1) QrMatch.Found(byArtist[0]) else QrMatch.Similar(sameTitle.take(MAX_CANDIDATES))
        }
        val similar = keys.keyed
            .map { (song, key) -> song to similarity(key, kt) }
            .filter { it.second >= SIMILAR_MIN }
            .sortedByDescending { it.second }
            .take(MAX_CANDIDATES)
            .map { it.first }
        return if (similar.isEmpty()) QrMatch.Missing else QrMatch.Similar(similar)
    }

    // the library's "same song" rule (SongMatch)
    fun sameArtist(a: String, b: String): Boolean = SongMatch.sameArtistKey(TextKey.of(a), TextKey.of(b))

    /** 1 = equal; by edit distance (letters swapped, missing or extra). */
    fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return 1.0 - prev[b.length].toDouble() / maxOf(a.length, b.length)
    }
}
