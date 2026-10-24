package com.juliano.stagebook.ui.setlists

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.juliano.stagebook.data.LibraryRepository
import com.juliano.stagebook.domain.QrMatch
import com.juliano.stagebook.domain.QrPayload
import com.juliano.stagebook.domain.SetlistMatcher
import com.juliano.stagebook.domain.TextKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** Uma música da biblioteca que pode entrar no setlist lido. */
@Immutable
data class SongRef(val id: String, val title: String, val author: String)

/**
 * Uma linha da conferência: a música do QR, o que achou na biblioteca e a escolhida (null = fica de fora).
 * [found] = achou sem dúvida; senão, [candidates] são as parecidas para o usuário escolher.
 */
@Immutable
data class ScanRow(val title: String, val artist: String, val found: Boolean, val candidates: List<SongRef>, val chosen: String?)

enum class ScanProblem { NOT_SETLIST, INCOMPLETE }

sealed interface ScanUi {
    /** Câmera aberta; [problem] = o último QR lido não serviu. */
    data class Scanning(val problem: ScanProblem? = null) : ScanUi

    @Immutable
    data class Review(val name: String, val rows: List<ScanRow>) : ScanUi {
        val missing: List<ScanRow> get() = rows.filter { it.chosen == null }
    }
}

/**
 * Lê o QR do setlist (do previous app ou de outro Stagebook: o mesmo formato LETRAS3) e confere com a biblioteca deste
 * aparelho antes de criar. Toda música que não for achada é avisada antes de criar: um setlist de 15 nunca vira 12
 * em silêncio.
 */
@HiltViewModel
class ScanSetlistViewModel @Inject constructor(private val repository: LibraryRepository) : ViewModel() {
    private val _ui = MutableStateFlow<ScanUi>(ScanUi.Scanning())
    val ui: StateFlow<ScanUi> = _ui

    /** Texto de um QR lido pela câmera (já descompactado). */
    fun onText(text: String) {
        if (_ui.value !is ScanUi.Scanning) return
        val parsed = QrPayload.parse(text)
        if (parsed == null) {
            // começa como QR de setlist mas não fecha a conta: leitura cortada, ler de novo
            _ui.value = ScanUi.Scanning(if (text.startsWith(QrPayload.HEADER)) ScanProblem.INCOMPLETE else ScanProblem.NOT_SETLIST)
            return
        }
        val songs = repository.library.value.songs
        val rows = parsed.songs.map { e ->
            when (val m = SetlistMatcher.match(e, songs)) {
                is QrMatch.Found -> ScanRow(e.title, e.artist, true, listOf(m.song.ref()), m.song.id)
                is QrMatch.Similar -> ScanRow(e.title, e.artist, false, m.candidates.map { it.ref() }, null)
                QrMatch.Missing -> ScanRow(e.title, e.artist, false, emptyList(), null)
            }
        }
        _ui.value = ScanUi.Review(parsed.name, rows)
    }

    private fun com.juliano.stagebook.data.model.Song.ref() = SongRef(id, title, author)

    fun setName(name: String) = _ui.update { if (it is ScanUi.Review) it.copy(name = name) else it }

    /** Escolhe a música de uma linha com dúvida (null = deixa de fora). */
    fun choose(index: Int, songId: String?) = _ui.update { ui ->
        if (ui !is ScanUi.Review) ui else ui.copy(rows = ui.rows.mapIndexed { i, r -> if (i == index) r.copy(chosen = songId) else r })
    }

    fun scanAgain() { _ui.value = ScanUi.Scanning() }

    /** Já existe um setlist com esse nome (sem ligar para acento e caixa)? */
    fun existing(name: String): String? = repository.library.value.sets.firstOrNull { TextKey.of(it.name) == TextKey.of(name) }?.id

    /** Cria o setlist (ou troca as músicas do que tem o mesmo nome, se [replaceId]) e devolve o id. */
    fun create(replaceId: String? = null): String? {
        val ui = _ui.value as? ScanUi.Review ?: return null
        val ids = ui.rows.mapNotNull { it.chosen }
        val id = replaceId ?: repository.createSetlist(uniqueName(ui.name.trim()))
        repository.setSetlistItems(id, ids)
        return id
    }

    // "Criar outro" com o nome já usado: "Show (2)", "Show (3)"…
    private fun uniqueName(name: String): String {
        if (existing(name) == null) return name
        var n = 2
        while (existing("$name ($n)") != null) n++
        return "$name ($n)"
    }
}
