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
import br.app.stagebook.data.model.SiteKind
import br.app.stagebook.data.model.siteKind
import br.app.stagebook.data.model.sites
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
    // what is being imported: what the automatic profile asked for, otherwise the profile's own kind
    // a page shared from a browser carries no kind: a PDF address or one of the PDF sites opens the PDF browser
    private val kind: SiteKind get() = SiteKind.entries.firstOrNull { it.name == route.kind }
        ?: SiteKind.PDF.takeIf { pdfAddress(route.query, look.sites(SiteKind.PDF)) }
        ?: look.siteKind
    val home: String get() = look.sites(kind).firstOrNull()?.url ?: HOME
    /** Browsing for PDF scores: a tapped PDF becomes a song, and there is no "import this page". */
    val pdfMode: Boolean get() = kind == SiteKind.PDF
    fun urlFor(q: String): String = urlFor(q, when (kind) { SiteKind.CHORDS -> "cifra"; SiteKind.PDF -> "partitura pdf"; SiteKind.LYRICS -> "letra" })

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    /** Song ready to review: the id of the existing one with the same title, or null if it's new. */
    private val opened = Channel<String?>(Channel.BUFFERED)
    val openEditor = opened.receiveAsFlow()
    // imported without internet: the editor opens with a notice that artist, year and BPM weren't filled in (B-27)
    private val offline = Channel<Unit>(Channel.BUFFERED)
    val offlineImport = offline.receiveAsFlow()

    /** `raw` is what evaluateJavascript returns: the script's JSON string, in quotes. */
    fun onExtracted(raw: String?) {
        if (extracting) return   // double tap on Import opened the editor twice
        startRun { extract(raw) }
    }

    private var extracting = false
    // which run holds the lock: a run that asked about the tablature lets go before asking, and its own end must not
    // free the lock of the run the answer started
    private var runs = 0

    private fun startRun(work: suspend () -> Unit) {
        extracting = true
        val mine = ++runs
        viewModelScope.launch {
            try { work() } finally { if (runs == mine) extracting = false }
        }
    }

    // page with tablature waiting for the "import it too?" answer
    private var pendingTab: String? = null

    /** Answer to the tablature question: import with it (as a `{start_of_tab}` block) or without. */
    fun answerTab(include: Boolean) {
        if (extracting) return
        val raw = pendingTab ?: return
        pendingTab = null
        _uiState.update { it.copy(askTab = false) }
        startRun { extract(raw, include) }
    }

    private suspend fun extract(raw: String?, withTab: Boolean? = null) {
        // one engine for the app and the editor on the computer (PageImporter); busy covers the slow part, off the main
        // thread (audit 01/11)
        _uiState.update { it.copy(busy = true) }
        val r = try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { importer.fromScript(raw, withTab) }
        } finally {
            _uiState.update { it.copy(busy = false) }
        }
        when (r) {
            PageImport.AskTab -> {
                pendingTab = raw   // the answer comes back through answerTab
                // free before asking: an answer given the instant the question shows was dropped by the lock still held
                // (the Release's tests, 20/11), and the editor never opened
                extracting = false
                _uiState.update { it.copy(askTab = true) }
            }
            is PageImport.Problem -> _uiState.update { it.copy(problem = r.problem) }
            is PageImport.Song -> {
                draft.put(ImportDraft.Draft(r.title, r.artist, r.year, r.text))
                if (r.offline) offline.send(Unit)
                opened.send(repository.findSong(r.title, r.artist)?.id)
            }
        }
    }

    fun problemShown() = _uiState.update { it.copy(problem = null) }

    companion object {
        /** Address, domain or a Google search for "<word> <text>" (lyrics or chords, depending on the profile). */
        fun urlFor(q: String, word: String = "letra"): String {
            val s = asAddress(q.trim())
            return when {
                s.matches(Regex("(?i)^https?://\\S*$")) -> lowerSite(s)
                isWebAddress(s) -> lowerSite("https://$s")
                else -> "https://www.google.com/search?q=" + Uri.encode("$word ${q.trim()}")
            }
        }

        /**
         * Whether the whole text is a web address, as Android's own link finder sees it (the list of real site endings:
         * "imslp.org" and "stagebook.app.br" are, "ac.dc" and "Sr.Brasil" aren't), like a browser's bar (user, 22/11).
         */
        fun isWebAddress(s: String): Boolean {
            if (s.isEmpty() || s.any(Char::isWhitespace)) return false
            // judged in lower case: the finder knows "org", not the keyboard's "Org" (only for judging; the path keeps its case)
            val text = android.text.SpannableString(s.lowercase())
            if (!android.text.util.Linkify.addLinks(text, android.text.util.Linkify.WEB_URLS)) return false
            val links = text.getSpans(0, text.length, android.text.style.URLSpan::class.java)
            return links.size == 1 && text.getSpanStart(links[0]) == 0 && text.getSpanEnd(links[0]) == text.length
        }

        /**
         * A phone's keyboard may put a space after each period and capitals at the start ("HTTPS://clara. Imslp. Org/…",
         * user 22/11). The bar is also a search ("Sr. Brasil", "ac. dc" must stay words, user 22/11): the spaces right
         * after . / : go only when what is left starts with http(s):// or is a web address.
         */
        private fun asAddress(s: String): String {
            if (!s.contains(' ')) return s
            val joined = s.replace(Regex("(?<=[./:])\\s+"), "")
            if (joined.contains(' ')) return s
            return if (joined.matches(Regex("(?i)^https?://\\S*$")) || isWebAddress(joined)) joined else s
        }

        /** An address that is a PDF or is on one of the PDF score sites (a page shared from a browser, 22/11). */
        fun pdfAddress(query: String, pdfSites: List<br.app.stagebook.data.model.ImportSite>): Boolean {
            val uri = runCatching { query.trim().toUri() }.getOrNull() ?: return false
            if (uri.scheme?.lowercase() !in setOf("http", "https")) return false
            if (uri.path.orEmpty().lowercase().endsWith(".pdf")) return true
            val host = uri.host?.lowercase()?.removePrefix("www.") ?: return false
            return pdfSites.any { site -> site.url.toUri().host?.lowercase()?.removePrefix("www.")?.let { host == it || host.endsWith(".$it") } == true }
        }

        private fun lowerSite(url: String): String {
            val m = Regex("^([^:]+://)([^/?#]*)(.*)$").find(url) ?: return url
            return m.groupValues[1].lowercase() + m.groupValues[2].lowercase() + m.groupValues[3]
        }

        const val HOME = "https://www.letras.mus.br/"
    }
}
