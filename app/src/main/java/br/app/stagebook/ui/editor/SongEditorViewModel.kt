package br.app.stagebook.ui.editor

import br.app.stagebook.data.ImportQueue
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import br.app.stagebook.data.model.Look
import br.app.stagebook.domain.Chords
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import br.app.stagebook.data.ImportDraft
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.SongMetadata
import br.app.stagebook.data.model.Song
import br.app.stagebook.navigation.SongEditor
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
    val unsaved: Boolean = false,   // something typed or imported not saved yet
) {
    val canDelete get() = mode == EditorMode.EDIT || mode == EditorMode.IMPORTED_UPDATE
}

/** How the editor exits: close, or (when batch importing into a setlist) open the next song or the finished setlist. */
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

    /** Opened from the stage: where to put the cursor in the lyrics (-1 = nowhere, without opening the keyboard). */
    val cursor = route.cursor

    var uiState by mutableStateOf(SongEditorUiState())
        private set

    private val done = Channel<EditorExit>(Channel.BUFFERED)
    private var finished = false   // double tap on Save/Delete: only the first counts
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
                // imported over an existing one: keeps that title (it's the setlist key)
                title = song?.title ?: draft?.title.orEmpty(),
                author = draft?.artist?.ifBlank { null } ?: song?.author.orEmpty(),
                year = draft?.year?.ifBlank { null } ?: song?.year.orEmpty(),
                // imported without a key: the app computes it from the chords (the Key field shows it, and the user can
                // change it)
                text = draft?.text?.let(Chords::withDetectedKey) ?: song?.text.orEmpty(),
                unsaved = draft != null,
            )
            original = loaded
            // Android closed the app with the edit open (low memory on the Fire): what was typed comes back
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

    /** Stage look (editor preview). */
    val look: StateFlow<Look> = repository.library.map { it.look }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.library.value.look)

    /**
     * Moves [semitones] half steps up or down: rewrites the chords and the saved key (chords above the lyrics become
     * ChordPro first).
     */
    fun transpose(semitones: Int) {
        // no saved key: saves the one computed from the chords first, so the key moves with the chart
        val base = Chords.fromChordsOverLyrics(uiState.text).let { t ->
            if (Chords.key(t).isNotEmpty()) t else Chords.detectKey(t).takeIf { it.isNotEmpty() }?.let { Chords.setDirective(t, "key", it) } ?: t
        }
        update(text = Chords.transpose(base, semitones))
    }

    /** Capo: the chord diagrams stay the same (the sounding key changes). 0 removes the directive. */
    fun setCapo(capo: Int) {
        val now = capo.coerceIn(0, 12)
        // the tablature stays in frets without a capo: the capo goes up, the frets go up with it (user's decision,
        // 29/10)
        val shifted = br.app.stagebook.domain.Tablature.shift(uiState.text, now - Chords.capo(uiState.text))
        update(text = Chords.setDirective(shifted, "capo", now.takeIf { it > 0 }?.toString().orEmpty()))
    }

    /** BPM in the song details (empty removes it); the metronome starts at it. */
    fun setTempo(bpm: String) = update(text = Chords.setDirective(uiState.text, "tempo", bpm.filter(Char::isDigit).take(3)))

    fun save() {
        if (finished) return
        val s = uiState
        val title = s.title.trim()
        if (title.isEmpty()) { uiState = s.copy(error = EditorError.TITLE_EMPTY); return }
        val other = repository.findSong(title, s.author.trim())
        if (other != null && other.id != route.songId) { uiState = s.copy(error = EditorError.TITLE_TAKEN); return }
        // part of the saved song kept so nothing the editor doesn't show is lost (e.g. stage zoom)
        val base = route.songId?.let(repository::song) ?: Song()
        val id = repository.saveSong(base.copy(id = route.songId.orEmpty(), title = title, author = s.author.trim(), year = s.year.trim(), text = Chords.fromChordsOverLyrics(s.text)))   // chords are saved as ChordPro
        metadata.completeLater(id)   // artist or year blank: MusicBrainz fills them in
        finished = true
        // batch importing into a setlist: the song takes its place and the queue moves on
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
