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

enum class EditorError { TITLE_EMPTY, TITLE_TAKEN, NOT_SONG }

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
    val tags: List<String> = emptyList(),   // saved with the song on Save, like the rest (user, 01/11: tags in the editor)
) {
    val canDelete get() = mode == EditorMode.EDIT || mode == EditorMode.IMPORTED_UPDATE

    /** Key, capo and BPM, read once per state (the header, the fields and each keystroke use them; audit 01/11). */
    val meta: br.app.stagebook.domain.SongMeta by lazy { Chords.meta(text) }
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
    private val tracks: br.app.stagebook.data.TrackStore,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<SongEditor>()
    private val draft = if (route.fromImport) importDraft.take() else null

    /** Opened from the stage: where to put the cursor in the lyrics (-1 = nowhere, without opening the keyboard). */
    val cursor = route.cursor

    var uiState by mutableStateOf(SongEditorUiState())
        private set

    // a new song gets its id now (kept across rotation): the backing track file is named after it, so a new song can
    // get one too (user, 30/10)
    private val songId: String = route.songId ?: savedStateHandle.get<String>(KEY_NEW_ID) ?: repository.newId().also { savedStateHandle[KEY_NEW_ID] = it }

    /** The song's backing track ("" = none). */
    var track by mutableStateOf(savedStateHandle.get<String>(KEY_TRACK) ?: route.songId?.let(repository::song)?.track.orEmpty())
        private set

    /** The track's own name, to show ("" = unknown: attached before names were kept). */
    var trackName by mutableStateOf(savedStateHandle.get<String>(KEY_TRACK_NAME) ?: route.songId?.let(repository::song)?.trackName.orEmpty())
        private set

    /** Links the chosen file right away (copied into the app); saving reads the song again, so it's kept. */
    suspend fun attachTrack(uri: android.net.Uri): Boolean {
        val name = tracks.copy(songId, uri) ?: return false
        val shown = tracks.displayName(uri)
        val old = track
        // a saved song keeps it right away; a new one takes it when saved
        if (route.songId != null) repository.updateSong(songId) { it.copy(track = name, trackName = shown) }
        if (old != name) tracks.remove(old)
        keepTrack(name, shown)
        return true
    }

    fun removeTrack() {
        if (route.songId != null) repository.updateSong(songId) { it.copy(track = "", trackName = "") }
        tracks.remove(track)
        keepTrack("", "")
    }

    private fun keepTrack(name: String, shown: String) {
        track = name
        trackName = shown
        savedStateHandle[KEY_TRACK] = name
        savedStateHandle[KEY_TRACK_NAME] = shown
    }

    // a new song left without saving: the backing track copied for it goes away
    override fun onCleared() {
        if (route.songId == null && !finished) tracks.remove(track)
    }

    private val done = Channel<EditorExit>(Channel.BUFFERED)
    private var finished = false   // double tap on Save/Delete: only the first counts
    val onDone = done.receiveAsFlow()

    init {
        viewModelScope.launch {
            repository.loaded.first { it }
            val song = route.songId?.let(repository::song)
            // the import draft lives in memory: if Android closes the app, the fields saved below bring it back, and
            // this keeps it marked as an import not saved yet (audit 01/11)
            if (draft != null) savedStateHandle[KEY_IMPORTED] = true
            val imported = draft != null || savedStateHandle.get<Boolean>(KEY_IMPORTED) == true
            val loaded = SongEditorUiState(
                loading = false,
                mode = when {
                    imported && song != null -> EditorMode.IMPORTED_UPDATE
                    imported -> EditorMode.IMPORTED_NEW
                    song != null -> EditorMode.EDIT
                    else -> EditorMode.NEW
                },
                // imported over an existing one: keeps that title (it's the setlist key)
                title = song?.title ?: draft?.title.orEmpty(),
                author = draft?.artist?.ifBlank { null } ?: song?.author.orEmpty(),
                year = draft?.year?.ifBlank { null } ?: song?.year.orEmpty(),
                // imported without a key: the app computes it from the chords (the Key field shows it, and the user can
                // change it)
                // the app's own lines (hidden chords, picked shapes) stay out of the text box and come back on save
                text = draft?.text?.let(Chords::withDetectedKey) ?: br.app.stagebook.domain.InternalMarks.split(song?.text.orEmpty()).first,
                unsaved = imported,
                tags = song?.tags.orEmpty(),
            )
            original = loaded
            // Android closed the app with the edit open (low memory on the Fire): what was typed comes back
            val kept = savedStateHandle.get<Array<String>>(KEY_FIELDS)
            if (kept == null && draft != null) savedStateHandle[KEY_FIELDS] = arrayOf(loaded.title, loaded.author, loaded.year, loaded.text)
            val keptTags = savedStateHandle.get<ArrayList<String>>(KEY_TAGS) ?: loaded.tags
            uiState = if (kept != null && kept.size == 4) loaded.edited(kept[0], kept[1], kept[2], kept[3], keptTags) else loaded.edited(loaded.title, loaded.author, loaded.year, loaded.text, keptTags)
        }
    }

    private var original = SongEditorUiState()

    private fun SongEditorUiState.edited(title: String, author: String, year: String, text: String, tags: List<String> = this.tags): SongEditorUiState {
        val next = copy(title = title, author = author, year = year, text = text, tags = tags)
        val changed = title != original.title || author != original.author || year != original.year || text != original.text || tags != original.tags
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

    /** Every tag in the library (the choices in the tags window). */
    fun allTags(): List<String> = br.app.stagebook.domain.Tags.all(repository.library.value.songs)

    fun onTags(add: List<String>, remove: List<String>) {
        val t = br.app.stagebook.domain.Tags.apply(uiState.tags, add, remove)
        uiState = uiState.edited(uiState.title, uiState.author, uiState.year, uiState.text, t)
        savedStateHandle[KEY_TAGS] = ArrayList(t)
    }

    /** Stage look (editor preview). */
    val look: StateFlow<Look> = repository.library.map { it.look }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.library.value.look)

    /**
     * Moves [semitones] half steps up or down: rewrites the chords and the saved key (chords above the lyrics become
     * ChordPro first).
     */
    fun transpose(semitones: Int) = update(text = Chords.transposeSong(uiState.text, semitones))

    /** Capo: the chord diagrams stay the same (the sounding key changes). 0 removes the directive. */
    fun setCapo(capo: Int) = update(text = Chords.withCapo(uiState.text, capo))

    /** BPM in the song details (empty removes it); the metronome starts at it. */
    fun setTempo(bpm: String) = update(text = Chords.setDirective(uiState.text, "tempo", bpm.filter(Char::isDigit).take(3)))

    fun save() {
        if (finished) return
        val s = uiState
        val title = s.title.trim()
        if (title.isEmpty()) { uiState = s.copy(error = EditorError.TITLE_EMPTY); return }
        val other = repository.findSong(title, s.author.trim())
        if (other != null && other.id != songId) { uiState = s.copy(error = EditorError.TITLE_TAKEN); return }
        // only song text is saved (the same filter as the band: size, no file disguised as text)
        if (!br.app.stagebook.domain.BandContent.songOk(title, s.author.trim(), s.year.trim(), s.text)) { uiState = s.copy(error = EditorError.NOT_SONG); return }
        // part of the saved song kept so nothing the editor doesn't show is lost (e.g. stage zoom)
        val base = route.songId?.let(repository::song) ?: Song()
        val internal = br.app.stagebook.domain.InternalMarks.split(base.text).second
        val id = repository.saveSong(base.copy(id = songId, track = track, trackName = trackName, title = title, author = s.author.trim(), year = s.year.trim(), tags = s.tags, text = Chords.withDetectedKey(Chords.fromChordsOverLyrics(br.app.stagebook.domain.InternalMarks.join(s.text, internal)))))   // chords are saved as ChordPro; the key the editor shows is saved too (31/10)
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
        const val KEY_TAGS = "editor_tags"
        const val KEY_IMPORTED = "editor_imported"
        const val KEY_NEW_ID = "editor_new_id"
        const val KEY_TRACK = "editor_track"
        const val KEY_TRACK_NAME = "editor_track_name"
    }
}
