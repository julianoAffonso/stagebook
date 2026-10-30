package br.app.stagebook.ui.importer

import androidx.core.net.toUri
import br.app.stagebook.domain.Chords
import br.app.stagebook.domain.ChordImport
import br.app.stagebook.domain.Tablature
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import br.app.stagebook.data.ImportDraft
import br.app.stagebook.data.ImportQueue
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.MusicBrainz
import br.app.stagebook.domain.LyricsCleaner
import br.app.stagebook.domain.PageData
import br.app.stagebook.domain.PageMeta
import br.app.stagebook.navigation.Importer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

enum class ImportProblem { NOT_FOUND, UNREADABLE }

@Immutable
/** [askTab]: the page has tablature and the app asks whether it comes in (T22, user's decision: ask every time). */
data class ImportUiState(val busy: Boolean = false, val problem: ImportProblem? = null, val askTab: Boolean = false)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val musicBrainz: MusicBrainz,
    private val draft: ImportDraft,
    savedStateHandle: SavedStateHandle,
    private val queue: ImportQueue,
    private val deezer: br.app.stagebook.data.DeezerBpm,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<Importer>()
    val initialQuery = route.query

    // single import: a queue left behind (exited midway) can't take this song
    init { if (!route.queued) queue.stop() }

    /** Batch import for a setlist (null = single import). */
    val queueState: StateFlow<ImportQueue.State?> = if (route.queued) queue.state else MutableStateFlow(null)
    fun queueSearch(): String? = if (route.queued) queue.searchUrl() else null
    fun queueSiteName(): String = queue.state.value?.let { queue.sites().getOrNull(it.site)?.name }.orEmpty()
    fun queueHost(): String = queue.state.value?.let { queue.sites().getOrNull(it.site)?.url }
        ?.let { it.toUri().host.orEmpty().removePrefix("www.") }.orEmpty()

    /** Not found on this site: moves to the next in the list; null = no more sites. */
    fun queueFailover(): String? = queue.nextSite()?.let { queue.tryNextSite(); queue.searchUrl() }
    fun queueSkip(): String? { queue.skip(); return queue.searchUrl() }
    fun queueStop() = queue.stop()

    /** Where the importer opens: the profile's default site (chords or lyrics), and what the search looks for. */
    private val look get() = repository.library.value.look
    val home: String get() = (if (look.chords) look.chordSites else look.lyricSites).firstOrNull()?.url ?: HOME
    fun urlFor(q: String): String = urlFor(q, if (look.chords) "cifra" else "letra")

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    /** Song ready to review: the id of the existing one with the same title, or null if it's new. */
    private val opened = Channel<String?>(Channel.BUFFERED)
    val openEditor = opened.receiveAsFlow()

    private val json = Json { ignoreUnknownKeys = true }

    /** `raw` is what evaluateJavascript returns: the script's JSON string, in quotes. */
    fun onExtracted(raw: String?) {
        if (extracting) return   // double tap on Import opened the editor twice
        extracting = true
        viewModelScope.launch {
            try { extract(raw) } finally { extracting = false }
        }
    }

    private var extracting = false

    // page with tablature waiting for the "import it too?" answer
    private var pendingTab: String? = null

    /** Answer to the tablature question: import with it (as a `{start_of_tab}` block) or without. */
    fun answerTab(include: Boolean) {
        val raw = pendingTab ?: return
        pendingTab = null
        _uiState.update { it.copy(askTab = false) }
        viewModelScope.launch { extract(raw, include) }
    }

    private suspend fun extract(raw: String?, withTab: Boolean? = null) {
        run {
            val parsed = runCatching { json.decodeFromString<PageData>(json.decodeFromString<String>(raw.orEmpty())) }.getOrNull()
            if (parsed == null) { _uiState.update { it.copy(problem = ImportProblem.UNREADABLE) }; return }
            // tablature on the page: ask first (the answer comes back through answerTab)
            if (withTab == null && (Tablature.hasTab(parsed.chordText) || Tablature.hasTab(parsed.text))) {
                pendingTab = raw
                _uiState.update { it.copy(askTab = true) }
                return
            }
            val tab: (String) -> String = { t -> if (withTab == true) Tablature.wrap(t) else Tablature.strip(t) }
            val page = parsed.copy(text = tab(parsed.text), chordText = tab(parsed.chordText))
            // lyrics and chords profile + page with chords: comes in as ChordPro (with key and capo); otherwise, lyrics
            // only
            val chordSource = page.chordText.ifBlank { page.text }
            var text = if (repository.library.value.look.chords && ChordImport.hasChords(chordSource)) {
                ChordImport.toChordPro(chordSource, page.key, page.capo)
            } else {
                // lyrics only: no chords, but the page's key and capo are kept (hidden in this profile; not lost if the
                // profile changes or the song goes to someone who plays)
                var lyrics = LyricsCleaner.clean(page.text.ifBlank { Chords.stripChords(Chords.fromChordsOverLyrics(page.chordText)) })
                val capo = ChordImport.capoOf(page.capo, page.chordText)
                val key = ChordImport.keyOf(page.key, page.chordText)
                if (capo > 0) lyrics = Chords.setDirective(lyrics, "capo", capo.toString())
                if (key.isNotEmpty()) lyrics = Chords.setDirective(lyrics, "key", key)
                lyrics
            }
            if (text.lines().size < 3) { _uiState.update { it.copy(problem = ImportProblem.NOT_FOUND) }; return }
            // BPM from the page (or written in the text): saved in the song, the metronome starts at it
            val bpm = ChordImport.bpmOf(page.bpm, page.chordText.ifBlank { page.text })
            if (bpm > 0 && Chords.tempo(text) == 0) text = Chords.setDirective(text, "tempo", bpm.toString())
            _uiState.update { it.copy(busy = true) }
            val meta = PageMeta.pick(page)
            var artist = meta.artist
            var year = PageMeta.yearOf(page)
            // year of the first release on MusicBrainz (the site usually lacks it); on a poor connection it doesn't
            // keep the singer on "loading" for more than 8 s
            val net = repository.library.value.look.internetOn   // essential mode (T19): no lookups
            if (net && meta.title.isNotEmpty()) musicBrainz.lookupWithin(meta.title, artist, LOOKUP_TIMEOUT_MS)?.let { m ->
                if (artist.isBlank()) artist = m.artist
                if (m.year.isNotEmpty()) year = m.year
            }
            // no BPM on the page or in the text: Deezer suggests one (the musician checks it in Song details)
            if (net && Chords.tempo(text) == 0 && meta.title.isNotEmpty()) deezer.lookupWithin(meta.title, artist, LOOKUP_TIMEOUT_MS)?.let { bpm ->
                text = Chords.setDirective(text, "tempo", bpm.toString())
            }
            draft.put(ImportDraft.Draft(meta.title, artist, year, text))
            _uiState.update { it.copy(busy = false) }
            opened.send(repository.findSong(meta.title, artist)?.id)
        }
    }

    fun problemShown() = _uiState.update { it.copy(problem = null) }

    companion object {
        private const val LOOKUP_TIMEOUT_MS = 8_000L

        /** Address, domain or a Google search for "<word> <text>" (lyrics or chords, depending on the profile). */
        fun urlFor(q: String, word: String = "letra"): String {
            val s = q.trim()
            return when {
                s.matches(Regex("(?i)^https?://.*")) -> s
                s.matches(Regex("(?i)^[\\w.-]+\\.[a-z]{2,}(/.*)?$")) -> "https://$s"
                else -> "https://www.google.com/search?q=" + Uri.encode("$word $s")
            }
        }

        const val HOME = "https://www.letras.mus.br/"
    }
}
