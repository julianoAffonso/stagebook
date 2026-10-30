package br.app.stagebook.ui.importer

import br.app.stagebook.data.ImportProblem
import br.app.stagebook.data.PageImport
import androidx.core.net.toUri
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
import br.app.stagebook.navigation.Importer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


@Immutable
/** [askTab]: the page has tablature and the app asks whether it comes in (T22, user's decision: ask every time). */
data class ImportUiState(val busy: Boolean = false, val problem: ImportProblem? = null, val askTab: Boolean = false)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val importer: br.app.stagebook.data.PageImporter,
    private val draft: ImportDraft,
    savedStateHandle: SavedStateHandle,
    private val queue: ImportQueue,
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
        // one engine for the app and the editor on the computer (PageImporter)
        when (val r = importer.fromScript(raw, withTab)) {
            PageImport.AskTab -> {
                pendingTab = raw   // the answer comes back through answerTab
                _uiState.update { it.copy(askTab = true) }
            }
            is PageImport.Problem -> _uiState.update { it.copy(problem = r.problem) }
            is PageImport.Song -> {
                _uiState.update { it.copy(busy = true) }
                draft.put(ImportDraft.Draft(r.title, r.artist, r.year, r.text))
                _uiState.update { it.copy(busy = false) }
                opened.send(repository.findSong(r.title, r.artist)?.id)
            }
        }
    }

    fun problemShown() = _uiState.update { it.copy(problem = null) }

    companion object {
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
