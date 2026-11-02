package br.app.stagebook.domain

import br.app.stagebook.data.model.Song

/**
 * The library's title keys ([TextKey.of]), worked out once per library: matching a 40-song setlist against 2000
 * songs used to normalize every title again for every entry (~80 thousand times, frozen screen after a QR scan; audit
 * 30/10). The library list is replaced on every change, so the last one is kept by identity.
 */
class TitleKeys private constructor(songs: List<Song>) {
    /** Each song with its title key, in library order. */
    val keyed: List<Pair<Song, String>> = songs.map { it to TextKey.of(it.title) }

    /** Songs by title key, in library order. */
    val byKey: Map<String, List<Song>> = keyed.groupBy({ it.second }, { it.first })

    /** Title keys that more than one song shares. */
    val repeated: Set<String> = byKey.filterValues { it.size > 1 }.keys

    companion object {
        @Volatile private var last: Pair<List<Song>, TitleKeys>? = null

        fun of(songs: List<Song>): TitleKeys {
            last?.let { (list, keys) -> if (list === songs) return keys }
            return TitleKeys(songs).also { last = songs to it }
        }
    }
}
