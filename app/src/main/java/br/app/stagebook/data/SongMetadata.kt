package br.app.stagebook.data

import br.app.stagebook.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** When saving a song without an artist or year, fills them in from MusicBrainz (without blocking the screen). */
@Singleton
class SongMetadata @Inject constructor(
    private val repository: LibraryRepository,
    private val musicBrainz: MusicBrainz,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun completeLater(songId: String) {
        val song = repository.song(songId) ?: return
        if (song.title.isBlank() || (song.author.isNotBlank() && song.year.isNotBlank())) return
        scope.launch {
            val info = musicBrainz.lookup(song.title, song.author) ?: return@launch
            repository.updateSong(songId) { cur ->
                cur.copy(author = cur.author.ifBlank { info.artist }, year = cur.year.ifBlank { info.year })
            }
        }
    }
}

/** Imported lyrics waiting for review in the editor (the navigation route carries only ids, not the lyrics). */
@Singleton
class ImportDraft @Inject constructor() {
    data class Draft(val title: String, val artist: String, val year: String, val text: String)

    private var pending: Draft? = null

    fun put(draft: Draft) { pending = draft }

    fun take(): Draft? = pending.also { pending = null }
}
