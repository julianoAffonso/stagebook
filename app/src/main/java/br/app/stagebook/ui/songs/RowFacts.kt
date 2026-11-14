package br.app.stagebook.ui.songs

import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.Chords
import br.app.stagebook.domain.SongGroups
import br.app.stagebook.domain.SongMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** What a row shows besides the title and the artist, read from the song once and kept while it doesn't change. */
class RowFacts(val text: String, val title: String, val meta: SongMeta, val letter: String, val hasTrack: Boolean)

/**
 * Key, tempo, capo and the index letter of every song, for the lists that show them (the song list, and picking songs
 * for a setlist). Read off the main thread and only when the songs change, and each song's text is read again only if
 * it changed: saving a zoom doesn't go through 200 lyrics on a slow tablet. One reader per view model.
 */
class RowFactsReader {
    @Volatile private var kept = emptyMap<String, RowFacts>()

    fun of(songs: Flow<List<Song>>): Flow<Map<String, RowFacts>> = songs.distinctUntilChanged()
        .map { list ->
            val old = kept
            list.associate { s ->
                val was = old[s.id]
                val track = s.track.isNotEmpty()
                s.id to when {
                    was == null || was.text != s.text -> RowFacts(s.text, s.title, Chords.meta(s.text), SongGroups.letter(s.title), track)
                    was.title != s.title || was.hasTrack != track -> RowFacts(s.text, s.title, was.meta, SongGroups.letter(s.title), track)
                    else -> was
                }
            }.also { kept = it }
        }
        .flowOn(Dispatchers.Default)
}
