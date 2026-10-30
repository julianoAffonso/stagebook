package br.app.stagebook.domain

/**
 * When two songs are the same: same title (ignoring accents/case/punctuation) and same artist. A blank artist on either
 * side counts as "any". That way "It's My Life" by Bon Jovi and by Talk Talk live side by side, and importing again
 * updates instead of duplicating.
 */
object SongMatch {
    fun same(titleA: String, artistA: String, titleB: String, artistB: String): Boolean =
        TextKey.of(titleA) == TextKey.of(titleB) && sameArtistKey(TextKey.of(artistA), TextKey.of(artistB))

    /** Artists already as keys ([TextKey.of]): for callers comparing many songs without rebuilding the keys. */
    fun sameArtistKey(a: String, b: String): Boolean = a.isEmpty() || b.isEmpty() || a == b || a.contains(b) || b.contains(a)
}
