package com.juliano.stagebook.ui.importer

import androidx.core.net.toUri
import com.juliano.stagebook.domain.Chords
import com.juliano.stagebook.domain.ChordImport
import com.juliano.stagebook.domain.Tablature
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.juliano.stagebook.data.ImportDraft
import com.juliano.stagebook.data.ImportQueue
import com.juliano.stagebook.data.LibraryRepository
import com.juliano.stagebook.data.MusicBrainz
import com.juliano.stagebook.domain.LyricsCleaner
import com.juliano.stagebook.domain.PageData
import com.juliano.stagebook.domain.PageMeta
import com.juliano.stagebook.navigation.Importer
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
/** [askTab]: a página tem tablatura e o app pergunta se ela entra (T22, decisão do usuário: perguntar a cada vez). */
data class ImportUiState(val busy: Boolean = false, val problem: ImportProblem? = null, val askTab: Boolean = false)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val musicBrainz: MusicBrainz,
    private val draft: ImportDraft,
    savedStateHandle: SavedStateHandle,
    private val queue: ImportQueue,
    private val deezer: com.juliano.stagebook.data.DeezerBpm,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<Importer>()
    val initialQuery = route.query

    // importação avulsa: uma fila que ficou para trás (saiu no meio) não pode pegar esta música
    init { if (!route.queued) queue.stop() }

    /** Importação em lote para um setlist (null = importação avulsa). */
    val queueState: StateFlow<ImportQueue.State?> = if (route.queued) queue.state else MutableStateFlow(null)
    fun queueSearch(): String? = if (route.queued) queue.searchUrl() else null
    fun queueSiteName(): String = queue.state.value?.let { queue.sites().getOrNull(it.site)?.name }.orEmpty()
    fun queueHost(): String = queue.state.value?.let { queue.sites().getOrNull(it.site)?.url }
        ?.let { it.toUri().host.orEmpty().removePrefix("www.") }.orEmpty()

    /** Não achou neste site: passa para o próximo da lista; null = não há mais sites. */
    fun queueFailover(): String? = queue.nextSite()?.let { queue.tryNextSite(); queue.searchUrl() }
    fun queueSkip(): String? { queue.skip(); return queue.searchUrl() }
    fun queueStop() = queue.stop()

    /** Onde o importador abre: o site padrão do perfil (cifra ou letra), e o que a busca procura. */
    private val look get() = repository.library.value.look
    val home: String get() = (if (look.chords) look.chordSites else look.lyricSites).firstOrNull()?.url ?: HOME
    fun urlFor(q: String): String = urlFor(q, if (look.chords) "cifra" else "letra")

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    /** Música pronta para revisar: o id da que já existe com o mesmo título, ou null se é nova. */
    private val opened = Channel<String?>(Channel.BUFFERED)
    val openEditor = opened.receiveAsFlow()

    private val json = Json { ignoreUnknownKeys = true }

    /** `raw` é o que o evaluateJavascript devolve: a string JSON do script, entre aspas. */
    fun onExtracted(raw: String?) {
        if (extracting) return   // toque duplo em Importar abria o editor duas vezes
        extracting = true
        viewModelScope.launch {
            try { extract(raw) } finally { extracting = false }
        }
    }

    private var extracting = false

    // página com tablatura esperando a resposta "importar junto?"
    private var pendingTab: String? = null

    /** Resposta da pergunta da tablatura: importa com ela (em bloco `{start_of_tab}`) ou sem. */
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
            // tablatura na página: pergunta antes (a resposta volta por answerTab)
            if (withTab == null && (Tablature.hasTab(parsed.chordText) || Tablature.hasTab(parsed.text))) {
                pendingTab = raw
                _uiState.update { it.copy(askTab = true) }
                return
            }
            val tab: (String) -> String = { t -> if (withTab == true) Tablature.wrap(t) else Tablature.strip(t) }
            val page = parsed.copy(text = tab(parsed.text), chordText = tab(parsed.chordText))
            // perfil letra e cifra + página com cifra: entra em ChordPro (com tom e capo); senão, só a letra
            val chordSource = page.chordText.ifBlank { page.text }
            var text = if (repository.library.value.look.chords && ChordImport.hasChords(chordSource)) {
                ChordImport.toChordPro(chordSource, page.key, page.capo)
            } else {
                // só letra: sem acordes, mas o tom e o capo da página ficam guardados (escondidos neste perfil; não se
                // perdem se o perfil mudar ou se a música for para quem toca)
                var lyrics = LyricsCleaner.clean(page.text.ifBlank { Chords.stripChords(Chords.fromChordsOverLyrics(page.chordText)) })
                val capo = ChordImport.capoOf(page.capo, page.chordText)
                val key = ChordImport.keyOf(page.key, page.chordText)
                if (capo > 0) lyrics = Chords.setDirective(lyrics, "capo", capo.toString())
                if (key.isNotEmpty()) lyrics = Chords.setDirective(lyrics, "key", key)
                lyrics
            }
            if (text.lines().size < 3) { _uiState.update { it.copy(problem = ImportProblem.NOT_FOUND) }; return }
            // BPM da página (ou escrito no texto): guardado na música, o metrônomo começa nele
            val bpm = ChordImport.bpmOf(page.bpm, page.chordText.ifBlank { page.text })
            if (bpm > 0 && Chords.tempo(text) == 0) text = Chords.setDirective(text, "tempo", bpm.toString())
            _uiState.update { it.copy(busy = true) }
            val meta = PageMeta.pick(page)
            var artist = meta.artist
            var year = PageMeta.yearOf(page)
            // ano do primeiro lançamento no MusicBrainz (o site costuma não ter); sem internet boa,
            // não segura o vocalista no "carregando" mais que 8 s
            val net = repository.library.value.look.internetOn   // modo essencial (T19): sem consultas
            if (net && meta.title.isNotEmpty()) musicBrainz.lookupWithin(meta.title, artist, LOOKUP_TIMEOUT_MS)?.let { m ->
                if (artist.isBlank()) artist = m.artist
                if (m.year.isNotEmpty()) year = m.year
            }
            // sem BPM na página nem no texto: o Deezer sugere (o músico confere nos Dados da música)
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

        /** Endereço, domínio ou busca no Google por "<palavra> <texto>" (letra ou cifra, conforme o perfil). */
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
