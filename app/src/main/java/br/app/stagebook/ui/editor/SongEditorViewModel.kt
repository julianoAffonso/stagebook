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
    val midi: br.app.stagebook.domain.SongMidi = br.app.stagebook.domain.SongMidi(),   // saved on Save too (user, 07/11)
) {
    val canDelete get() = mode == EditorMode.EDIT || mode == EditorMode.IMPORTED_UPDATE

    /** Key, capo and BPM, read once per state (the header, the fields and each keystroke use them; audit 01/11). */
    val meta: br.app.stagebook.domain.SongMeta by lazy { Chords.meta(text) }
}

/** What the editor's card says about the song's PDF: its name, pages (0 = not read yet) and size; [lost]: the file isn't on this device. */
@Immutable
data class EditorPdf(
    val name: String, val pages: Int = 0, val bytes: Long = 0L, val lost: Boolean = false,
    val file: java.io.File? = null,   // the PDF itself, for the card's preview of its first page
    val invert: Boolean = false,      // its colours turned over on the stage (this song's, or what Settings says)
    val crops: List<br.app.stagebook.data.model.PdfCrop> = emptyList(),   // the trims of its pages (this device)
)

/** How the editor exits: close, or (when batch importing into a setlist) open the next song or the finished setlist. */
sealed interface EditorExit {
    data object Close : EditorExit
    data class Saved(val songId: String) : EditorExit
    data object NextImport : EditorExit
    data class SetlistReady(val setId: String) : EditorExit

    /** Previous or next song of the queue: the editor opens on that song in place of this one. */
    data class Moved(val songId: String) : EditorExit
}

/** Where this song is in the queue the editor was opened from: "3 / 12" and which arrows work. */
@Immutable
data class EditorPosition(val index: Int, val total: Int) {
    val number get() = index + 1
    val hasPrev get() = index > 0
    val hasNext get() = index < total - 1
}

@HiltViewModel
class SongEditorViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val metadata: SongMetadata,
    importDraft: ImportDraft,
    private val savedStateHandle: SavedStateHandle,
    private val importQueue: ImportQueue,
    private val tracks: br.app.stagebook.data.TrackStore,
    private val queue: br.app.stagebook.data.EditorQueue,
    // the song's PDF: copied and checked on attach. Without a store (tests of the other parts) attaching does nothing
    private val pdfs: br.app.stagebook.data.PdfStore? = null,
    private val inspector: br.app.stagebook.data.PdfInspector = br.app.stagebook.data.PdfInspector(),
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

    /** The song's PDF file ("" = none), its own name and what the card says about it; see [pdfInfo]. */
    var pdf by mutableStateOf(savedStateHandle.get<String>(KEY_PDF) ?: route.songId?.let(repository::song)?.pdf.orEmpty())
        private set
    var pdfName by mutableStateOf(savedStateHandle.get<String>(KEY_PDF_NAME) ?: route.songId?.let(repository::song)?.pdfName.orEmpty())
        private set
    /** This song's PDF with its colours turned over on the stage; null = as Settings says. */
    var pdfInvert by mutableStateOf<Boolean?>(savedStateHandle.get<Boolean>(KEY_PDF_INVERT) ?: route.songId?.let(repository::song)?.pdfInvert)
        private set
    /** The trims of the PDF's pages (this device only). */
    var pdfCrops by mutableStateOf(route.songId?.let(repository::song)?.pdfCrops.orEmpty())
        private set
    private var pdfPages by mutableStateOf(0)
    private var pdfBytes by mutableStateOf(0L)

    /** The card that takes the place of the lyrics field: null when the song is not a PDF (a PDF whose file is gone is [EditorPdf.lost]). */
    val pdfInfo: EditorPdf? get() = if (pdf.isEmpty() && pdfName.isEmpty()) null else EditorPdf(
        pdfName, pdfPages, pdfBytes, lost = pdf.isEmpty(),
        file = pdf.takeIf { it.isNotEmpty() }?.let { pdfs?.file(it) }?.takeIf { it.isFile },
        invert = pdfInvert ?: repository.library.value.look.pdfInvert,
        crops = pdfCrops,
    )

    init {
        // a saved song's page count and size for the card: read once, off the main thread
        if (pdf.isNotEmpty()) viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val file = pdfs?.file(pdf)?.takeIf { it.isFile } ?: return@launch
            pdfBytes = file.length()
            (inspector.check(file) as? br.app.stagebook.data.PdfCheck.Ok)?.let { pdfPages = it.pages }
        }
    }

    /**
     * Attaches the chosen PDF: copied into the app and checked ([br.app.stagebook.data.PdfInspector]); refused with the reason
     * otherwise (nothing kept). A saved song keeps it right away, like the track; a new one takes it when saved. A song with
     * no title takes the file's name. The text stays as it is, hidden, and comes back if the PDF goes. A new PDF clears the
     * old one's highlighter marks (other pages).
     */
    suspend fun attachPdf(uri: android.net.Uri): br.app.stagebook.data.PdfAttach {
        val store = pdfs ?: return br.app.stagebook.data.PdfAttach.Refused(br.app.stagebook.data.PdfProblem.UNREADABLE)
        val result = store.attach(songId, uri, inspector)
        if (result !is br.app.stagebook.data.PdfAttach.Saved) return result
        val shown = store.displayName(uri).ifBlank { "PDF" }
        val old = pdf
        if (route.songId != null) repository.updateSong(songId) { it.copy(pdf = result.name, pdfName = shown, pdfMarks = emptyList(), pdfCrops = emptyList(), pdfPage = 0) }
        if (old != result.name) store.remove(old)
        keepPdf(result.name, shown, result.pages, result.bytes)
        if (uiState.title.isBlank()) onTitle(shown.substringBeforeLast('.').trim())
        return result
    }

    /** A trim set for a page (or for all of them), kept at once for a saved song like the invert. */
    fun setPdfCrop(crop: br.app.stagebook.data.model.PdfCrop) { pdfCrops = br.app.stagebook.domain.PdfCrops.set(pdfCrops, crop); keepCrops() }

    /** The trim of [page] ([br.app.stagebook.data.model.PdfCrop.ALL] for the one for all pages) taken off. */
    fun clearPdfCrop(page: Int) { pdfCrops = br.app.stagebook.domain.PdfCrops.clear(pdfCrops, page); keepCrops() }

    private fun keepCrops() { if (route.songId != null) repository.updateSong(songId) { it.copy(pdfCrops = pdfCrops) } }

    /** The colours turned over for this song's PDF: kept at once for a saved song, like the track. */
    fun setPdfInvert(on: Boolean) {
        pdfInvert = on
        savedStateHandle[KEY_PDF_INVERT] = on
        if (route.songId != null) repository.updateSong(songId) { it.copy(pdfInvert = on) }
    }

    /** Takes the PDF out: the file goes and the lyrics are the song again. */
    fun removePdf() {
        pdfInvert = null
        pdfCrops = emptyList()
        savedStateHandle.remove<Boolean>(KEY_PDF_INVERT)
        if (route.songId != null) repository.updateSong(songId) { it.copy(pdf = "", pdfName = "", pdfMarks = emptyList(), pdfInvert = null, pdfCrops = emptyList(), pdfPage = 0) }
        pdfs?.remove(pdf)
        keepPdf("", "", 0, 0L)
    }

    private fun keepPdf(name: String, shown: String, pages: Int, bytes: Long) {
        pdf = name
        pdfName = shown
        pdfPages = pages
        pdfBytes = bytes
        pdfCrops = emptyList()   // another file: the old trims were of other pages
        savedStateHandle[KEY_PDF] = name
        savedStateHandle[KEY_PDF_NAME] = shown
    }

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

    // a new song left without saving: the backing track copied for it goes away. The queue goes when the editor closes, but
    // not when it only moves to the neighbouring song (that editor starts before this one is cleared)
    override fun onCleared() {
        if (route.songId == null && !finished) { tracks.remove(track); pdfs?.remove(pdf) }
        if (!moving) queue.clearIf(queueGeneration)
    }

    private var moving = false

    // the queue as it was when this editor started: only that one is emptied when it closes (see EditorQueue.generation)
    private val queueGeneration = queue.generation

    /** Where the open song is in the queue (null: no queue, or this song isn't in it: an import, a new song). */
    val position: StateFlow<EditorPosition?> = queue.state.map { q -> positionIn(q) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, positionIn(queue.state.value))

    private fun positionIn(q: br.app.stagebook.data.EditorQueue.State?): EditorPosition? {
        val id = route.songId ?: return null
        if (route.fromImport || q == null) return null
        val at = if (q.ids.getOrNull(q.index) == id) q.index else q.ids.indexOf(id)
        return if (at < 0) null else EditorPosition(at, q.ids.size)
    }

    /** The song [step] places away (−1 previous, +1 next) that still exists; deleted ones are skipped. */
    private fun neighbour(step: Int): Pair<Int, String>? {
        val q = queue.state.value ?: return null
        var at = (position.value ?: return null).index + step
        while (at in q.ids.indices) {
            if (repository.song(q.ids[at]) != null) return at to q.ids[at]
            at += step
        }
        return null
    }

    /** Previous or next song without saving (the screen asks first when something is unsaved). */
    fun go(step: Int) {
        if (finished) return
        val (at, id) = neighbour(step) ?: return
        finished = true
        moving = true
        queue.moveTo(at)
        done.trySend(EditorExit.Moved(id))
    }

    /** "Save and go on": saves this song and, only if it saved, moves to the neighbour. */
    fun saveAndGo(step: Int) {
        if (neighbour(step) == null) return
        if (persistSong() == null) return
        go(step)
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
                text = br.app.stagebook.domain.InternalMarks.split(draft?.text?.let(Chords::withDetectedKey) ?: song?.text.orEmpty()).first,
                unsaved = imported,
                tags = song?.tags.orEmpty(),
                midi = song?.midi ?: br.app.stagebook.domain.SongMidi(),
            )
            original = loaded
            // Android closed the app with the edit open (low memory on the Fire): what was typed comes back
            val kept = savedStateHandle.get<Array<String>>(KEY_FIELDS)
            if (kept == null && draft != null) savedStateHandle[KEY_FIELDS] = arrayOf(loaded.title, loaded.author, loaded.year, loaded.text)
            val keptTags = savedStateHandle.get<ArrayList<String>>(KEY_TAGS) ?: loaded.tags
            val keptMidi = savedStateHandle.get<String>(KEY_MIDI)?.let { runCatching { midiJson.decodeFromString(br.app.stagebook.domain.SongMidi.serializer(), it) }.getOrNull() } ?: loaded.midi
            uiState = if (kept != null && kept.size == 4) loaded.edited(kept[0], kept[1], kept[2], kept[3], keptTags, keptMidi) else loaded.edited(loaded.title, loaded.author, loaded.year, loaded.text, keptTags, keptMidi)
        }
    }

    private var original = SongEditorUiState()

    private fun SongEditorUiState.edited(title: String, author: String, year: String, text: String, tags: List<String> = this.tags, midi: br.app.stagebook.domain.SongMidi = this.midi): SongEditorUiState {
        val next = copy(title = title, author = author, year = year, text = text, tags = tags, midi = midi)
        val changed = title != original.title || author != original.author || year != original.year || text != original.text || tags != original.tags ||
            midi != original.midi
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

    /** The song's MIDI on entering and leaving the stage, from its window; kept across a closed app like the fields. */
    fun onMidi(m: br.app.stagebook.domain.SongMidi) {
        uiState = uiState.edited(uiState.title, uiState.author, uiState.year, uiState.text, midi = m)
        savedStateHandle[KEY_MIDI] = midiJson.encodeToString(br.app.stagebook.domain.SongMidi.serializer(), m)
    }

    private val midiJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /** Stage look (editor preview). */
    val look: StateFlow<Look> = repository.library.map { it.look }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.library.value.look)

    /**
     * Moves [semitones] half steps up or down: rewrites the chords and the saved key (chords above the lyrics become
     * ChordPro first).
     */
    fun transpose(semitones: Int) {
        // a PDF song has no chords to move (its text is hidden and left as it is): the key is only written down, a step at a
        // time, starting from C when none was (user, 15/11)
        if (pdfInfo != null) {
            val key = uiState.meta.key
            update(text = Chords.setDirective(uiState.text, "key", if (key.isEmpty()) "C" else Chords.transposeKey(key, semitones)))
            return
        }
        update(text = Chords.transposeSong(uiState.text, semitones))
    }

    /** Capo: the chord diagrams stay the same (the sounding key changes). 0 removes the directive. */
    fun setCapo(capo: Int) = update(text = Chords.withCapo(uiState.text, capo))

    /** BPM in the song details (empty removes it); the metronome starts at it. */
    fun setTempo(bpm: String) = update(text = Chords.setDirective(uiState.text, "tempo", bpm.filter(Char::isDigit).take(3)))

    fun save() {
        if (finished) return
        val id = persistSong() ?: return
        finished = true
        // batch importing into a setlist: the song takes its place and the queue moves on
        val batch = importQueue.state.value
        if (route.fromImport && batch != null) {
            importQueue.saved(id)
            done.trySend(if (importQueue.state.value != null) EditorExit.NextImport else EditorExit.SetlistReady(batch.setId))
        } else {
            done.trySend(EditorExit.Saved(id))
        }
    }

    /** Checks and saves the song; its id, or null (with the error on the screen) when it can't be saved. */
    private fun persistSong(): String? {
        val s = uiState
        val title = s.title.trim()
        if (title.isEmpty()) { uiState = s.copy(error = EditorError.TITLE_EMPTY); return null }
        val other = repository.findSong(title, s.author.trim())
        if (other != null && other.id != songId) { uiState = s.copy(error = EditorError.TITLE_TAKEN); return null }
        // only song text is saved (the same filter as the band: size, no file disguised as text)
        if (!br.app.stagebook.domain.BandContent.songOk(title, s.author.trim(), s.year.trim(), s.text)) { uiState = s.copy(error = EditorError.NOT_SONG); return null }
        // part of the saved song kept so nothing the editor doesn't show is lost (e.g. stage zoom)
        val base = route.songId?.let(repository::song) ?: Song()
        val id = repository.saveSong(base.copy(id = songId, track = track, trackName = trackName, pdf = pdf, pdfName = pdfName, pdfInvert = pdfInvert, pdfCrops = pdfCrops, pdfMarks = if (pdf == base.pdf) base.pdfMarks else emptyList(), title = title, author = s.author.trim(), year = s.year.trim(), tags = s.tags, midi = s.midi.takeUnless { it.isEmpty() }, text = Chords.withDetectedKey(Chords.fromChordsOverLyrics(br.app.stagebook.domain.InternalMarks.restore(s.text, base.text)))))   // chords are saved as ChordPro; the key the editor shows is saved too (31/10)
        metadata.completeLater(id)   // artist or year blank: MusicBrainz fills them in
        return id
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
        const val KEY_MIDI = "editor_midi"
        const val KEY_IMPORTED = "editor_imported"
        const val KEY_NEW_ID = "editor_new_id"
        const val KEY_TRACK = "editor_track"
        const val KEY_TRACK_NAME = "editor_track_name"
        const val KEY_PDF = "editor_pdf"
        const val KEY_PDF_NAME = "editor_pdf_name"
        const val KEY_PDF_INVERT = "editor_pdf_invert"
    }
}
