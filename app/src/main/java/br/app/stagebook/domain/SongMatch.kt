package br.app.stagebook.domain

/**
 * When two songs are the same: same title (ignoring accents/case/punctuation) and same artist. A blank artist on either
 * side counts as "any". That way "It's My Life" by Bon Jovi and by Talk Talk live side by side, and importing again
 * updates instead of duplicating.
 */
object SongMatch {
    fun same(titleA: String, artistA: String, titleB: String, artistB: String): Boolean =
        TextKey.of(titleA) == TextKey.of(titleB) && sameArtistKey(TextKey.of(artistA), TextKey.of(artistB))

    /**
     * Finds "the same song" ([same]) among many, fast: grouped by title key once, the artist compared only inside the
     * group. Merging a 1000-song backup into 2000 songs went from ~2 million comparisons to ~3000 lookups (audit 30/10).
     * Same answer as checking the items in order: the first match wins.
     */
    class Index<T>(items: Iterable<T>, private val title: (T) -> String, private val artist: (T) -> String) {
        private val byTitle = HashMap<String, MutableList<Pair<T, String>>>()

        init { items.forEach(::add) }

        fun add(item: T) { byTitle.getOrPut(TextKey.of(title(item))) { mutableListOf() } += item to TextKey.of(artist(item)) }

        fun find(title: String, artist: String): T? {
            val a = TextKey.of(artist)
            return byTitle[TextKey.of(title)]?.firstOrNull { sameArtistKey(it.second, a) }?.first
        }
    }

    /** Artists already as keys ([TextKey.of]): for callers comparing many songs without rebuilding the keys. */
    fun sameArtistKey(a: String, b: String): Boolean = a.isEmpty() || b.isEmpty() || a == b || a.contains(b) || b.contains(a)
}
