package com.juliano.stagebook.domain

import com.juliano.stagebook.data.model.Song

/**
 * Busca da lista de músicas e do "adicionar ao setlist". Normalizar texto (acentos, caixa) é caro no
 * Fire: as chaves são feitas uma vez por versão da biblioteca, e cada tecla só procura nelas.
 */
class SongSearch private constructor(private val entries: List<Pair<Song, String>>) {

    /** Em ordem de título; com a busca vazia, todas. */
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
