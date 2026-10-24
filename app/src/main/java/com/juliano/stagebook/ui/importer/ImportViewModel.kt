package com.juliano.stagebook.ui.importer

import com.juliano.stagebook.domain.Chords
import com.juliano.stagebook.domain.ChordImport
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.juliano.stagebook.data.ImportDraft
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
data class ImportUiState(val busy: Boolean = false, val problem: ImportProblem? = null)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val musicBrainz: MusicBrainz,
    private val draft: ImportDraft,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val initialQuery = savedStateHandle.toRoute<Importer>().query

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

    private suspend fun extract(raw: String?) {
        run {
            val page = runCatching { json.decodeFromString<PageData>(json.decodeFromString<String>(raw.orEmpty())) }.getOrNull()
            if (page == null) { _uiState.update { it.copy(problem = ImportProblem.UNREADABLE) }; return }
            // perfil letra e cifra + página com cifra: entra em ChordPro (com tom e capo); senão, só a letra
            val chordSource = page.chordText.ifBlank { page.text }
            val text = if (repository.library.value.look.chords && ChordImport.hasChords(chordSource)) {
                ChordImport.toChordPro(chordSource, page.key, page.capo)
            } else {
                LyricsCleaner.clean(page.text.ifBlank { Chords.stripChords(Chords.fromChordsOverLyrics(page.chordText)) })
            }
            if (text.lines().size < 3) { _uiState.update { it.copy(problem = ImportProblem.NOT_FOUND) }; return }
            _uiState.update { it.copy(busy = true) }
            val meta = PageMeta.pick(page)
            var artist = meta.artist
            var year = PageMeta.yearOf(page)
            // ano do primeiro lançamento no MusicBrainz (o site costuma não ter); sem internet boa,
            // não segura o vocalista no "carregando" mais que 8 s
            if (meta.title.isNotEmpty()) musicBrainz.lookupWithin(meta.title, artist, LOOKUP_TIMEOUT_MS)?.let { m ->
                if (artist.isBlank()) artist = m.artist
                if (m.year.isNotEmpty()) year = m.year
            }
            draft.put(ImportDraft.Draft(meta.title, artist, year, text))
            _uiState.update { it.copy(busy = false) }
            opened.send(repository.findSong(meta.title, artist)?.id)
        }
    }

    fun problemShown() = _uiState.update { it.copy(problem = null) }

    companion object {
        private const val LOOKUP_TIMEOUT_MS = 8_000L

        /** Endereço, domínio ou busca no Google por "letra <texto>". */
        fun urlFor(q: String): String {
            val s = q.trim()
            return when {
                s.matches(Regex("(?i)^https?://.*")) -> s
                s.matches(Regex("(?i)^[\\w.-]+\\.[a-z]{2,}(/.*)?$")) -> "https://$s"
                else -> "https://www.google.com/search?q=" + Uri.encode("letra $s")
            }
        }

        const val HOME = "https://www.letras.mus.br/"
    }
}
