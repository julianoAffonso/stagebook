package com.juliano.stagebook.ui.setlists

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.juliano.stagebook.data.ImportQueue
import com.juliano.stagebook.data.LibraryRepository
import com.juliano.stagebook.data.SetlistCloud
import com.juliano.stagebook.data.model.Song
import com.juliano.stagebook.domain.QrMatch
import com.juliano.stagebook.domain.QrPayload
import com.juliano.stagebook.domain.SetlistMatcher
import com.juliano.stagebook.domain.ShareCode
import com.juliano.stagebook.domain.SongMatch
import com.juliano.stagebook.domain.TextKey
import com.juliano.stagebook.navigation.ScanSetlist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Uma música da biblioteca que pode entrar no setlist lido. */
@Immutable
data class SongRef(val id: String, val title: String, val author: String)

/**
 * Uma linha da conferência: a música do setlist, o que achou na biblioteca e a escolhida (null = fica de fora).
 * [found] = achou sem dúvida; senão, [candidates] são as parecidas para o usuário escolher. [carried] = não está na
 * biblioteca, mas veio junto com o setlist (pela internet): entra na biblioteca ao criar.
 */
@Immutable
data class ScanRow(
    val title: String,
    val artist: String,
    val found: Boolean,
    val candidates: List<SongRef>,
    val chosen: String?,
    val carried: Boolean = false,
)

enum class ScanProblem { NOT_SETLIST, INCOMPLETE }

enum class CodeProblem { NOT_FOUND, OFFLINE }

sealed interface ScanUi {
    /** Câmera aberta; [problem] = o último QR lido não serviu. */
    data class Scanning(val problem: ScanProblem? = null) : ScanUi

    /** Buscando pelo código da internet, ou o que deu errado. */
    data class Fetching(val problem: CodeProblem? = null) : ScanUi

    /** [songsOnly]: vieram músicas avulsas (sem nome de setlist): só entram na biblioteca, nenhum setlist é criado. */
    @Immutable
    data class Review(val name: String, val rows: List<ScanRow>, val songsOnly: Boolean = false) : ScanUi {
        /** As que vão ficar de fora: sem escolha e sem ter vindo junto. */
        val missing: List<ScanRow> get() = rows.filter { it.chosen == null && !it.carried }
    }
}

/**
 * Lê o setlist (QR de outro aparelho, ou o código da internet: o mesmo formato do QR) e confere com a
 * biblioteca deste aparelho antes de criar. Toda música que não for achada é avisada antes de criar: um setlist de 15
 * nunca vira 12 em silêncio. As músicas que vieram junto só entram no lugar das que faltam (as daqui nunca mudam).
 */
@HiltViewModel
class ScanSetlistViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val importQueue: ImportQueue,
    private val cloud: SetlistCloud,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    var code: String = savedStateHandle.toRoute<ScanSetlist>().code
        private set

    private val _ui = MutableStateFlow<ScanUi>(if (code.isEmpty()) ScanUi.Scanning() else ScanUi.Fetching())
    val ui: StateFlow<ScanUi> = _ui

    // músicas que vieram junto, por linha da conferência
    private var carried: Map<Int, SetlistCloud.SharedSong> = emptyMap()

    init { if (code.isNotEmpty()) fetch() }

    /** Busca o setlist pelo código (de novo, depois de um erro). */
    fun fetch() {
        _ui.value = ScanUi.Fetching()
        viewModelScope.launch {
            when (val r = cloud.receive(code)) {
                is SetlistCloud.Received.Ok -> review(r.text, r.songs)
                SetlistCloud.Received.NotFound -> _ui.value = ScanUi.Fetching(CodeProblem.NOT_FOUND)
                SetlistCloud.Received.Offline -> _ui.value = ScanUi.Fetching(CodeProblem.OFFLINE)
            }
        }
    }

    /** Texto de um QR lido pela câmera (já descompactado). */
    fun onText(text: String) {
        if (_ui.value !is ScanUi.Scanning) return
        // QR de um código da internet (foto do diálogo de quem enviou): busca pelo código
        ShareCode.fromQr(text)?.let { code = it; fetch(); return }
        if (QrPayload.parse(text) == null) {
            // começa como QR de setlist mas não fecha a conta: leitura cortada, ler de novo
            _ui.value = ScanUi.Scanning(if (text.startsWith(QrPayload.HEADER)) ScanProblem.INCOMPLETE else ScanProblem.NOT_SETLIST)
            return
        }
        review(text, emptyList())
    }

    internal fun review(text: String, songs: List<SetlistCloud.SharedSong>) {
        val parsed = QrPayload.parse(text) ?: run { _ui.value = ScanUi.Fetching(CodeProblem.NOT_FOUND); return }
        val library = repository.library.value.songs
        val came = mutableMapOf<Int, SetlistCloud.SharedSong>()
        val songsOnly = parsed.name.isEmpty()
        fun came(e: QrPayload.Entry) = songs.firstOrNull { s ->
            TextKey.of(s.title) == TextKey.of(e.title) && SongMatch.sameArtistKey(TextKey.of(s.author), TextKey.of(e.artist))
        }
        val rows = parsed.songs.mapIndexed { i, e ->
            val m = SetlistMatcher.match(e, library)
            // músicas avulsas: a parecida não é a mesma — a que veio entra como nova (no setlist, o usuário escolhe)
            val c = if (m is QrMatch.Missing || (songsOnly && m is QrMatch.Similar)) came(e) else null
            if (c != null) came[i] = c
            when {
                m is QrMatch.Found -> ScanRow(e.title, e.artist, true, listOf(m.song.ref()), m.song.id)
                c != null -> ScanRow(e.title, e.artist, false, emptyList(), null, carried = true)
                m is QrMatch.Similar -> ScanRow(e.title, e.artist, false, m.candidates.map { it.ref() }, null)
                else -> ScanRow(e.title, e.artist, false, emptyList(), null)
            }
        }
        carried = came
        _ui.value = ScanUi.Review(parsed.name, rows, songsOnly)
    }

    private fun Song.ref() = SongRef(id, title, author)

    fun setName(name: String) = _ui.update { if (it is ScanUi.Review) it.copy(name = name) else it }

    /** Escolhe a música de uma linha com dúvida (null = deixa de fora). */
    fun choose(index: Int, songId: String?) = _ui.update { ui ->
        if (ui !is ScanUi.Review) ui else ui.copy(rows = ui.rows.mapIndexed { i, r -> if (i == index) r.copy(chosen = songId) else r })
    }

    fun scanAgain() { if (code.isEmpty()) _ui.value = ScanUi.Scanning() else fetch() }

    /** Músicas avulsas: as que vieram e não estão aqui entram na biblioteca; devolve quantas. */
    fun addSongs(): Int {
        carried.values.forEach { s -> repository.saveSong(Song(title = s.title, author = s.author, year = s.year, text = s.text)) }
        return carried.size
    }

    /** Já existe um setlist com esse nome (sem ligar para acento e caixa)? */
    fun existing(name: String): String? = repository.library.value.sets.firstOrNull { TextKey.of(it.name) == TextKey.of(name) }?.id

    /** Cria o setlist (ou troca as músicas do que tem o mesmo nome, se [replaceId]) e devolve o id. */
    fun create(replaceId: String? = null): String? = createWithSlots(replaceId)?.first

    // as que vieram junto entram na biblioteca agora; devolve o setlist e, na ordem dele, o id de cada linha (null = falta)
    private fun createWithSlots(replaceId: String?): Pair<String, List<String?>>? {
        val ui = _ui.value as? ScanUi.Review ?: return null
        val slots = ui.rows.mapIndexed { i, r ->
            r.chosen ?: carried[i]?.let { s -> repository.saveSong(Song(title = s.title, author = s.author, year = s.year, text = s.text)) }
        }
        val id = replaceId ?: repository.createSetlist(uniqueName(ui.name.trim()))
        repository.setSetlistItems(id, slots.filterNotNull())
        return id to slots
    }

    /**
     * Cria o setlist com as achadas (e as que vieram junto) e começa a importar as que faltam, uma a uma, no lugar delas
     * (fila guiada no importador). As que ficaram com dúvida e sem escolha continuam de fora.
     */
    fun createAndImport(replaceId: String? = null): Boolean {
        val ui = _ui.value as? ScanUi.Review ?: return false
        val (id, slots) = createWithSlots(replaceId) ?: return false
        val name = repository.library.value.setlist(id)?.name ?: ui.name
        val missing = ui.rows.withIndex().filter { (i, r) -> slots[i] == null && r.candidates.isEmpty() }
            .map { (i, r) -> ImportQueue.Item(r.title, r.artist, i) }
        importQueue.start(id, name, slots, missing)
        return missing.isNotEmpty()
    }

    // "Criar outro" com o nome já usado: "Show (2)", "Show (3)"…
    private fun uniqueName(name: String): String {
        if (existing(name) == null) return name
        var n = 2
        while (existing("$name ($n)") != null) n++
        return "$name ($n)"
    }
}
