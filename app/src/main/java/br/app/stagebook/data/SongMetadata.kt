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
    private companion object { const val LATER_TRIES = 3; const val LATER_PAUSE_MS = 3_000L }

    fun completeLater(songId: String) {
        val song = repository.song(songId) ?: return
        if (song.title.isBlank() || (song.author.isNotBlank() && song.year.isNotBlank())) return
        scope.launch {
            // in the background, nobody waits: it gives way to an import that is waiting for its year, and tries again a little later
            var info: MusicBrainz.Info? = null
            for (attempt in 0 until LATER_TRIES) {
                info = musicBrainz.lookup(song.title, song.author, patient = false)
                if (info != null) break
                kotlinx.coroutines.delay(LATER_PAUSE_MS)
            }
            val found = info ?: return@launch
            repository.updateSong(songId) { cur ->
                cur.copy(author = cur.author.ifBlank { found.artist }, year = cur.year.ifBlank { found.year })
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
