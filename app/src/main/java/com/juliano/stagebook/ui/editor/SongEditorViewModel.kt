package com.juliano.stagebook.ui.editor

import com.juliano.stagebook.data.ImportQueue
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import com.juliano.stagebook.data.model.Look
import com.juliano.stagebook.domain.Chords
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.juliano.stagebook.data.ImportDraft
import com.juliano.stagebook.data.LibraryRepository
import com.juliano.stagebook.data.SongMetadata
import com.juliano.stagebook.data.model.Song
import com.juliano.stagebook.navigation.SongEditor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class EditorMode { NEW, EDIT, IMPORTED_NEW, IMPORTED_UPDATE }

enum class EditorError { TITLE_EMPTY, TITLE_TAKEN }

@Immutable
data class SongEditorUiState(
    val loading: Boolean = true,
    val mode: EditorMode = EditorMode.NEW,
    val title: String = "",
    val author: String = "",
    val year: String = "",
    val text: String = "",
    val error: EditorError? = null,
    val unsaved: Boolean = false,   // algo digitado ou importado que ainda não foi salvo
) {
    val canDelete get() = mode == EditorMode.EDIT || mode == EditorMode.IMPORTED_UPDATE
}

/** Como o editor sai: fechar, ou (importando em lote para um setlist) abrir a próxima música ou o setlist pronto. */
sealed interface EditorExit {
    data object Close : EditorExit
    data object NextImport : EditorExit
    data class SetlistReady(val setId: String) : EditorExit
}

@HiltViewModel
class SongEditorViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val metadata: SongMetadata,
    importDraft: ImportDraft,
    private val savedStateHandle: SavedStateHandle,
    private val importQueue: ImportQueue,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<SongEditor>()
    private val draft = if (route.fromImport) importDraft.take() else null

    /** Aberta do palco: onde pôr o cursor na letra (-1 = em lugar nenhum, sem abrir o teclado). */
    val cursor = route.cursor

    var uiState by mutableStateOf(SongEditorUiState())
        private set

    private val done = Channel<EditorExit>(Channel.BUFFERED)
    private var finished = false   // toque duplo em Salvar/Apagar: só o primeiro vale
    val onDone = done.receiveAsFlow()

    init {
        viewModelScope.launch {
            repository.loaded.first { it }
            val song = route.songId?.let(repository::song)
            val loaded = SongEditorUiState(
                loading = false,
                mode = when {
                    draft != null && song != null -> EditorMode.IMPORTED_UPDATE
                    draft != null -> EditorMode.IMPORTED_NEW
                    song != null -> EditorMode.EDIT
                    else -> EditorMode.NEW
                },
                // importada por cima de uma que já existe: mantém o título de lá (é a chave do setlist)
                title = song?.title ?: draft?.title.orEmpty(),
                author = draft?.artist?.ifBlank { null } ?: song?.author.orEmpty(),
                year = draft?.year?.ifBlank { null } ?: song?.year.orEmpty(),
                // importada sem tom: o app calcula pelos acordes (o campo Tom mostra, e o usuário pode trocar)
                text = draft?.text?.let(Chords::withDetectedKey) ?: song?.text.orEmpty(),
                unsaved = draft != null,
            )
            original = loaded
            // o Android fechou o app com a edição aberta (pouca memória no Fire): volta o que foi digitado
            val kept = savedStateHandle.get<Array<String>>(KEY_FIELDS)
            uiState = if (kept != null && kept.size == 4) loaded.edited(kept[0], kept[1], kept[2], kept[3]) else loaded
        }
    }

    private var original = SongEditorUiState()

    private fun SongEditorUiState.edited(title: String, author: String, year: String, text: String): SongEditorUiState {
        val next = copy(title = title, author = author, year = year, text = text)
        val changed = title != original.title || author != original.author || year != original.year || text != original.text
        return next.copy(unsaved = changed || original.unsaved)
    }

    private fun update(title: String = uiState.title, author: String = uiState.author, year: String = uiState.year, text: String = uiState.text) {
        uiState = uiState.edited(title, author, year, text)
        savedStateHandle[KEY_FIELDS] = arrayOf(title, author, year, text)
    }

    fun onTitle(v: String) { update(title = v); uiState = uiState.copy(error = null) }
    fun onAuthor(v: String) = update(author = v)
    fun onYear(v: String) = update(year = v.filter(Char::isDigit).take(4))
    fun onText(v: String) = update(text = v)

    /** Aparência do palco (prévia do editor). */
    val look: StateFlow<Look> = repository.library.map { it.look }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.library.value.look)

    /** Sobe ou desce [semitones] meios-tons: reescreve os acordes e o tom guardado (a cifra em cima vira ChordPro antes). */
    fun transpose(semitones: Int) = update(text = Chords.transpose(Chords.fromChordsOverLyrics(uiState.text), semitones))

    /** Capotraste: os desenhos dos acordes ficam iguais (muda o tom que soa). 0 tira a diretiva. */
    fun setCapo(capo: Int) = update(text = Chords.setDirective(uiState.text, "capo", capo.coerceIn(0, 12).takeIf { it > 0 }?.toString().orEmpty()))

    fun setKey(key: String) = update(text = Chords.setDirective(uiState.text, "key", key.trim()))

    /** BPM nos dados da música (vazio tira); o metrônomo começa nele. */
    fun setTempo(bpm: String) = update(text = Chords.setDirective(uiState.text, "tempo", bpm.filter(Char::isDigit).take(3)))

    fun save() {
        if (finished) return
        val s = uiState
        val title = s.title.trim()
        if (title.isEmpty()) { uiState = s.copy(error = EditorError.TITLE_EMPTY); return }
        val other = repository.findSong(title, s.author.trim())
        if (other != null && other.id != route.songId) { uiState = s.copy(error = EditorError.TITLE_TAKEN); return }
        // parte da música gravada para não perder o que o editor não mostra (ex.: zoom do palco)
        val base = route.songId?.let(repository::song) ?: Song()
        val id = repository.saveSong(base.copy(id = route.songId.orEmpty(), title = title, author = s.author.trim(), year = s.year.trim(), text = Chords.fromChordsOverLyrics(s.text)))   // a cifra é guardada em ChordPro
        metadata.completeLater(id)   // artista ou ano em branco: o MusicBrainz completa sozinho
        finished = true
        // importando em lote para um setlist: a música entra no lugar dela e a fila anda
        val queue = importQueue.state.value
        if (route.fromImport && queue != null) {
            importQueue.saved(id)
            done.trySend(if (importQueue.state.value != null) EditorExit.NextImport else EditorExit.SetlistReady(queue.setId))
        } else {
            done.trySend(EditorExit.Close)
        }
    }

    fun delete() {
        if (finished) return
        finished = true
        route.songId?.let(repository::deleteSong)
        done.trySend(EditorExit.Close)
    }

    private companion object {
        const val KEY_FIELDS = "editor_fields"
    }
}
