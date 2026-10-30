package com.juliano.stagebook.ui.setlists

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.channels.Channel
import com.juliano.stagebook.data.BandLink
import com.juliano.stagebook.data.BandInternet
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.juliano.stagebook.data.BandInbox
import com.juliano.stagebook.data.ImportQueue
import com.juliano.stagebook.data.LibraryRepository
import com.juliano.stagebook.data.SetlistCloud
import com.juliano.stagebook.data.model.Song
import com.juliano.stagebook.domain.QrMatch
import com.juliano.stagebook.domain.QrPayload
import com.juliano.stagebook.domain.SetlistMatcher
import com.juliano.stagebook.domain.ShareCode
import com.juliano.stagebook.domain.SongMatch
import com.juliano.stagebook.domain.SongMerge
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
    val fromBand: Boolean = false,   // a banda tinha (pedida na conferência)
    val differs: Boolean = false,    // já tenho, mas a que chegou é outra versão: o usuário escolhe
    val lyricsOnly: Boolean = false, // perfil letra e cifra, mas a que chegou veio sem cifra
    val wantChords: Boolean = false, // recusou a só-letra: vai buscar com cifra nos sites (fila de importação)
    val useIncoming: Boolean = false,
)

enum class ScanProblem { NOT_SETLIST, INCOMPLETE, NEWER }

enum class CodeProblem { NOT_FOUND, OFFLINE, NEWER }

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
    private val inbox: BandInbox,
    private val bandLink: BandLink,
    private val bandInternet: BandInternet,
    private val trust: com.juliano.stagebook.data.BandTrust,
    private val connectivity: com.juliano.stagebook.data.Connectivity,
) : ViewModel() {
    /** Quem mandou, se for da banda (o pedido pela internet vai para a caixa dele). */
    private fun senderId(): String? = (inbox.get(route.inbox)?.fromId ?: sender?.id)?.takeIf(trust::isMember)

    /** Nome de quem mandou, quando dá para pedir pela internet as que faltam (é da banda e há internet); senão null. */
    val internetFrom: StateFlow<String?> = combine(connectivity.online, repository.library.map { it.look.internetOn }) { a, b -> a && b }.map { on ->
        if (!on) null else senderId()?.let { id -> trust.member(id)?.name ?: fromName.ifBlank { null } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val internetAsked = Channel<Boolean>(Channel.BUFFERED)
    /** Resultado do pedido pela internet (true = foi). */
    val internetResult = internetAsked.receiveAsFlow()

    /** Pede pela internet a quem mandou as músicas que faltam (T12); elas chegam depois em "Recebidos da banda". */
    fun askInternet() {
        val ui = _ui.value as? ScanUi.Review ?: return
        val id = senderId() ?: return
        val missing = ui.rows.filter { r -> r.chosen == null && !r.carried }.map { BandLink.Wanted(it.title, it.artist) }
        if (missing.isEmpty()) return
        viewModelScope.launch { internetAsked.send(bandInternet.requestSongs(id, missing)) }
    }

    // quem mandou pelas 3 palavras: ao aceitar, vira da banda (e recebe o meu "ok")
    private var sender: com.juliano.stagebook.domain.BandProtocol.Hello? = null
    /** Quantos da banda estão na rede agora ("Pedir à banda" só aparece com alguém e com música faltando). */
    val bandOnline: StateFlow<Int> = bandLink.onlineMembers.map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _asking = MutableStateFlow(false)
    val asking: StateFlow<Boolean> = _asking

    private val bandAnswer = Channel<Pair<Int, Int>>(Channel.BUFFERED)
    /** (quantas a banda tinha, quantas foram pedidas), para o aviso. */
    val bandResult = bandAnswer.receiveAsFlow()

    /** A conferência com música faltando está aberta: procura a banda (fechou, para). */
    fun bandSearch(on: Boolean) = bandLink.setScreenOpen(on)

    /** Pede à banda as que faltam; as que chegarem entram como "veio da banda", no lugar delas. */
    fun askBand() {
        val ui = _ui.value as? ScanUi.Review ?: return
        if (_asking.value) return
        val missing = ui.rows.withIndex().filter { (_, r) -> r.chosen == null && !r.carried && !r.wantChords }
        if (missing.isEmpty()) return
        _asking.value = true
        viewModelScope.launch {
            val got = bandLink.askMissing(missing.map { (_, r) -> BandLink.Wanted(r.title, r.artist) })
            val now = _ui.value as? ScanUi.Review
            if (now != null) {
                val came = carried.toMutableMap()
                val rows = now.rows.mapIndexed { i, r ->
                    if (r.chosen != null || r.carried) return@mapIndexed r
                    val s = got.firstOrNull { TextKey.of(it.title) == TextKey.of(r.title) && SongMatch.sameArtistKey(TextKey.of(it.author), TextKey.of(r.artist)) }
                    if (s == null) r else { came[i] = s; r.copy(carried = true, fromBand = true, lyricsOnly = noChords(s)) }
                }
                carried = came
                _ui.value = now.copy(rows = rows)
                bandAnswer.send(rows.count { it.fromBand } to missing.size)
            }
            _asking.value = false
        }
    }
    private val route = savedStateHandle.toRoute<ScanSetlist>()
    var code: String = route.code
        private set

    /** Veio da banda (caixa de entrada): quem mandou, para o título; conferido, sai da caixa. */
    val fromName: String = inbox.get(route.inbox)?.fromName.orEmpty()
    private fun done() {
        if (route.inbox.isNotEmpty()) inbox.remove(route.inbox)
        if (code.isNotEmpty()) bandInternet.accepted(code, sender)
    }

    private val _ui = MutableStateFlow<ScanUi>(if (code.isEmpty() && route.inbox.isEmpty()) ScanUi.Scanning() else ScanUi.Fetching())
    val ui: StateFlow<ScanUi> = _ui

    // músicas que vieram junto, por linha da conferência
    private var carried: Map<Int, SetlistCloud.SharedSong> = emptyMap()
    // outra versão de uma música que já tenho, por linha (só usada se o usuário escolher)
    private var incoming: Map<Int, SetlistCloud.SharedSong> = emptyMap()

    // as só-letra recusadas, por linha (para voltar atrás em "Aceitar só a letra")
    private var declined: Map<Int, SetlistCloud.SharedSong> = emptyMap()

    /**
     * A que chegou só com a letra (perfil letra e cifra): [on] = não aceitar e buscar com cifra nos sites (ela fica como
     * "falta" e entra na fila de "Importar as que faltam"); false = aceitar só a letra de novo.
     */
    fun searchWithChords(index: Int, on: Boolean) {
        val ui = _ui.value as? ScanUi.Review ?: return
        val row = ui.rows.getOrNull(index) ?: return
        if (on && row.carried) {
            carried[index]?.let { declined = declined + (index to it) }
            carried = carried - index
        } else if (!on && row.wantChords) {
            declined[index]?.let { carried = carried + (index to it) }
        }
        _ui.value = ui.copy(rows = ui.rows.mapIndexed { i, r -> if (i == index) r.copy(carried = !on, wantChords = on) else r })
    }

    /** O que muda da minha para a recebida, na linha [index] (para "Ver o que muda"). */
    fun changes(index: Int): List<SongMerge.Change> {
        val ui = _ui.value as? ScanUi.Review ?: return emptyList()
        val mine = ui.rows.getOrNull(index)?.chosen?.let(repository::song) ?: return emptyList()
        val theirs = incoming[index] ?: return emptyList()
        return SongMerge.changes(mine.text, theirs.text)
    }

    /** Manter a minha (false) ou usar a recebida (true) na linha [index]. */
    fun useIncoming(index: Int, use: Boolean) = _ui.update { ui ->
        if (ui !is ScanUi.Review) ui else ui.copy(rows = ui.rows.mapIndexed { i, r -> if (i == index) r.copy(useIncoming = use) else r })
    }

    // as recebidas escolhidas entram por cima das minhas, sem apagar a minha cifra
    private fun applyIncoming(ui: ScanUi.Review) {
        ui.rows.forEachIndexed { i, r ->
            val theirs = incoming[i] ?: return@forEachIndexed
            val id = r.chosen ?: return@forEachIndexed
            if (r.useIncoming) repository.updateSong(id) { it.copy(text = SongMerge.accept(it.text, theirs.text)) }
        }
    }

    init {
        when {
            route.inbox.isNotEmpty() -> inbox.get(route.inbox)?.let {
                if (it.newer) _ui.value = ScanUi.Fetching(CodeProblem.NEWER) else review(it.text, it.songs)
            } ?: run { _ui.value = ScanUi.Fetching(CodeProblem.NOT_FOUND) }
            code.isNotEmpty() -> fetch()
        }
    }

    /** Busca o setlist pelo código (de novo, depois de um erro). */
    fun fetch() {
        _ui.value = ScanUi.Fetching()
        viewModelScope.launch {
            when (val r = cloud.receive(code)) {
                is SetlistCloud.Received.Ok -> { sender = r.from; review(r.text, r.songs) }
                SetlistCloud.Received.NotFound -> _ui.value = ScanUi.Fetching(CodeProblem.NOT_FOUND)
                SetlistCloud.Received.Offline -> _ui.value = ScanUi.Fetching(CodeProblem.OFFLINE)
                is SetlistCloud.Received.Newer -> _ui.value = ScanUi.Fetching(CodeProblem.NEWER)
            }
        }
    }

    /** Texto de um QR lido pela câmera (já descompactado). */
    fun onText(text: String) {
        if (_ui.value !is ScanUi.Scanning) return
        // QR de um código da internet (foto do diálogo de quem enviou): busca pelo código
        ShareCode.fromQr(text)?.let { code = it; fetch(); return }
        if (QrPayload.parse(text) == null) {
            // começa como QR de setlist mas não fecha a conta: leitura cortada, ler de novo; de formato mais novo: atualizar
            _ui.value = ScanUi.Scanning(
                when {
                    QrPayload.isNewer(text) -> ScanProblem.NEWER
                    text.startsWith(QrPayload.HEADER) -> ScanProblem.INCOMPLETE
                    else -> ScanProblem.NOT_SETLIST
                },
            )
            return
        }
        review(text, emptyList())
    }

    internal fun review(text: String, songs: List<SetlistCloud.SharedSong>) {
        val parsed = QrPayload.parse(text) ?: run { _ui.value = ScanUi.Fetching(CodeProblem.NOT_FOUND); return }
        val library = repository.library.value.songs
        val came = mutableMapOf<Int, SetlistCloud.SharedSong>()
        val other = mutableMapOf<Int, SetlistCloud.SharedSong>()
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
                m is QrMatch.Found -> {
                    // já tenho: se veio outra versão junto, a linha pergunta (a minha fica, a não ser que eu escolha)
                    val theirs = came(e)?.takeIf { !SongMerge.same(it.text, m.song.text) }
                    if (theirs != null) other[i] = theirs
                    ScanRow(e.title, e.artist, true, listOf(m.song.ref()), m.song.id, differs = theirs != null)
                }
                c != null -> ScanRow(e.title, e.artist, false, emptyList(), null, carried = true, lyricsOnly = noChords(c))
                m is QrMatch.Similar -> ScanRow(e.title, e.artist, false, m.candidates.map { it.ref() }, null)
                else -> ScanRow(e.title, e.artist, false, emptyList(), null)
            }
        }
        carried = came
        incoming = other
        _ui.value = ScanUi.Review(parsed.name, rows, songsOnly)
    }

    private fun Song.ref() = SongRef(id, title, author)

    // no perfil letra e cifra, avisa a música que chegou só com a letra (falta a cifra)
    private fun noChords(s: SetlistCloud.SharedSong) =
        repository.library.value.look.chords && !com.juliano.stagebook.domain.Chords.hasChords(s.text)

    fun setName(name: String) = _ui.update { if (it is ScanUi.Review) it.copy(name = name) else it }

    /** Escolhe a música de uma linha com dúvida (null = deixa de fora). */
    fun choose(index: Int, songId: String?) = _ui.update { ui ->
        if (ui !is ScanUi.Review) ui else ui.copy(rows = ui.rows.mapIndexed { i, r -> if (i == index) r.copy(chosen = songId) else r })
    }

    fun scanAgain() { if (code.isEmpty()) _ui.value = ScanUi.Scanning() else fetch() }

    /** Músicas avulsas: as que vieram e não estão aqui entram na biblioteca; devolve quantas. */
    fun addSongs(): Int {
        carried.values.forEach { s -> repository.saveSong(Song(title = s.title, author = s.author, year = s.year, text = com.juliano.stagebook.domain.Chords.withDetectedKey(s.text))) }
        (_ui.value as? ScanUi.Review)?.let(::applyIncoming)
        done()
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
            r.chosen ?: carried[i]?.let { s -> repository.saveSong(Song(title = s.title, author = s.author, year = s.year, text = com.juliano.stagebook.domain.Chords.withDetectedKey(s.text))) }
        }
        val id = replaceId ?: repository.createSetlist(uniqueName(ui.name.trim()))
        repository.setSetlistItems(id, slots.filterNotNull())
        applyIncoming(ui)
        done()
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
