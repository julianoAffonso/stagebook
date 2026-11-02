package br.app.stagebook.domain

import br.app.stagebook.data.model.Song

/**
 * Search for the song list and "add to setlist". Normalizing text (accents, case) is expensive on the Fire: keys are
 * built once per library version, and each keystroke only searches them.
 */
class SongSearch private constructor(private val entries: List<Pair<Song, String>>) {

    /** In title order; with an empty search, all of them. */
    fun find(query: String): List<Song> {
        val key = TextKey.of(query)
        return if (key.isEmpty()) entries.map { it.first } else entries.filter { it.second.contains(key) }.map { it.first }
    }

    companion object {
        fun of(songs: List<Song>) = SongSearch(
            songs.map { Triple(it, TextKey.of(it.title), TextKey.of(it.title + " " + it.author)) }
                .sortedBy { it.second }
                .map { it.first to it.third },
        )
    }
}
