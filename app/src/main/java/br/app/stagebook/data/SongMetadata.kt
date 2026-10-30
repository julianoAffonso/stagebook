package br.app.stagebook.data

import br.app.stagebook.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Ao salvar uma música sem artista ou sem ano, completa sozinho pelo MusicBrainz (sem travar a tela). */
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

/** Letra importada esperando revisão no editor (a rota de navegação leva só ids, não a letra). */
@Singleton
class ImportDraft @Inject constructor() {
    data class Draft(val title: String, val artist: String, val year: String, val text: String)

    private var pending: Draft? = null

    fun put(draft: Draft) { pending = draft }

    fun take(): Draft? = pending.also { pending = null }
}
