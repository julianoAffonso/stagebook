package com.juliano.stagebook.ui.setlists

import kotlinx.coroutines.flow.update
import com.juliano.stagebook.data.BandInternet
import com.juliano.stagebook.data.BandTrust
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.channels.Channel
import com.juliano.stagebook.data.BandLink
import com.juliano.stagebook.ui.share.CloudUi
import com.juliano.stagebook.domain.ShareCode
import com.juliano.stagebook.data.Connectivity
import com.juliano.stagebook.data.SetlistCloud
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.juliano.stagebook.data.LibraryRepository
import com.juliano.stagebook.domain.QrEncoder
import com.juliano.stagebook.domain.QrPayload
import com.juliano.stagebook.navigation.SetlistDetail
import com.juliano.stagebook.ui.songs.subtitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlinx.coroutines.Job

/** `key` é estável ao reordenar (a mesma música pode aparecer duas vezes em setlists antigos). */
@Immutable
data class SetItemUi(val key: String, val songId: String, val title: String, val subtitle: String, val missing: Boolean)

@Immutable
data class SetlistDetailUiState(
    val loading: Boolean = true,
    val exists: Boolean = true,
    val name: String = "",
    val items: List<SetItemUi> = emptyList(),
)

/** QR pronto: a imagem e quantas músicas vão nele (as removidas da biblioteca não vão). */
@Immutable
data class QrUi(val image: ImageBitmap, val name: String, val count: Int, val missing: Int)

@HiltViewModel
class SetlistDetailViewModel @Inject constructor(
    private val repository: LibraryRepository,
    savedStateHandle: SavedStateHandle,
    private val setlistCloud: SetlistCloud,
    private val connectivity: Connectivity,
    private val bandLink: BandLink,
    private val bandTrust: BandTrust,
    private val bandInternet: BandInternet,
    private val conduct: com.juliano.stagebook.data.BandConduct,
) : ViewModel() {
    /** "Conduzir a banda": manda o setlist (cada um diz o que falta) e quem conduz vai para o palco. */
    fun conductBand() {
        val lib = repository.library.value
        val set = lib.setlist(setId) ?: return
        val songs = set.items.mapNotNull(lib::song).map { com.juliano.stagebook.domain.Conduct.Item(it.title, it.author) }
        conduct.start(setId, set.name, songs)
    }

    val internetOn: StateFlow<Boolean> = repository.library.map { it.look.internetOn }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.library.value.look.internetOn)
    private val internet = combine(connectivity.online, internetOn) { a, b -> a && b }   // modo essencial (T19)
    private fun connectivityNow() = connectivity.isOnline()
    val setId = savedStateHandle.toRoute<SetlistDetail>().setId

    /** Com internet agora? O "Código pela internet" só aparece com ela (só observa com a tela aberta). */
    val online: StateFlow<Boolean> = connectivity.online.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _cloud = MutableStateFlow<CloudUi?>(null)
    val cloud: StateFlow<CloudUi?> = _cloud.asStateFlow()

    /** Envia o setlist pela internet (com as músicas, se [withSongs]); o código aparece num diálogo. */
    fun sendInternet(withSongs: Boolean) {
        if (_cloud.value == CloudUi.Sending) return
        _cloud.value = CloudUi.Sending
        viewModelScope.launch {
            val name = repository.library.value.setlist(setId)?.name.orEmpty()
            val sent = setlistCloud.send(setId, withSongs)
            // o QR do código: quem está do lado tira foto ou lê pelo "Ler QR"
            val qr = sent?.let { s ->
                withContext(Dispatchers.Default) { runCatching { QrEncoder.encode(ShareCode.qrText(s.code).toByteArray(), 600).asImageBitmap() }.getOrNull() }
            }
            sent?.let { bandInternet.remember(it.code); watchJoins(it.code) }
            _cloud.value = sent?.let { CloudUi.Sent(it.code, name, it.songs, it.skipped, qr) } ?: CloudUi.Failed
        }
    }

    fun dismissCloud() { _cloud.value = null; joinsJob?.cancel() }

    private var joinsJob: kotlinx.coroutines.Job? = null

    // com o diálogo das palavras aberto: confere de tempos em tempos quem aceitou (vira da banda); fechou, para
    private fun watchJoins(code: String) {
        joinsJob?.cancel()
        joinsJob = viewModelScope.launch {
            repeat(JOIN_CHECKS) {
                kotlinx.coroutines.delay(JOIN_EVERY_MS)
                val names = bandInternet.checkAcks(code)
                if (names.isNotEmpty()) _cloud.update { c -> (c as? CloudUi.Sent)?.let { it.copy(joined = it.joined + names) } ?: c }
            }
        }
    }

    /**
     * Para quantos da banda dá para mandar agora ("Mandar para a banda (N)"): todos, se alguém está no Wi-Fi ou há
     * internet; 0 = a opção não aparece.
     */
    val bandOnline: StateFlow<Int> = combine(bandTrust.members, bandLink.onlineMembers, internet) { members, lan, net ->
        if (members.isNotEmpty() && (lan.isNotEmpty() || net)) members.size else 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val bandSent = Channel<Int>(Channel.BUFFERED)
    /** Para quantos aparelhos da banda chegou o último envio (0 = ninguém recebeu). */
    val bandResult = bandSent.receiveAsFlow()

    /** A folha Compartilhar abriu/fechou: liga/desliga a procura da banda. */
    fun bandSearch(on: Boolean) = bandLink.setScreenOpen(on)

    /** Manda o setlist (com as músicas, se [withSongs]) para todos da banda na rede agora. */
    fun sendBand(withSongs: Boolean) {
        viewModelScope.launch {
            val pkg = setlistCloud.setlistPackage(setId, withSongs) ?: return@launch
            // perto (Wi-Fi) na hora; os outros pela internet, na caixa de cada par
            val lan = bandLink.send(pkg.first, pkg.second)
            val net = if (connectivityNow()) bandInternet.sendToBand(pkg.first, pkg.second, lan) else 0
            bandSent.send(lan.size + net)
        }
    }

    val uiState: StateFlow<SetlistDetailUiState> = combine(repository.loaded, repository.library) { loaded, lib ->
        val set = lib.setlist(setId)
        val seen = HashMap<String, Int>()
        SetlistDetailUiState(
            loading = !loaded,
            exists = set != null,
            name = set?.name.orEmpty(),
            items = set?.items.orEmpty().map { id ->
                val n = seen.merge(id, 1, Int::plus)!!
                val song = lib.song(id)
                SetItemUi(if (n == 1) id else "$id#$n", id, song?.title.orEmpty(), song?.subtitle().orEmpty(), song == null)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetlistDetailUiState())

    private val _qr = MutableStateFlow<QrUi?>(null)
    val qr: StateFlow<QrUi?> = _qr.asStateFlow()

    fun move(from: Int, to: Int) = repository.moveItem(setId, from, to)

    fun remove(index: Int, songId: String) = repository.removeItem(setId, index, songId)

    /** Aviso do limite do QR: [LimitNotice.Songs] (não cabe para transmitir) ou [LimitNotice.Name] (nome longo demais). */
    enum class LimitNotice { Songs, Name }

    private val _limit = MutableStateFlow<LimitNotice?>(null)
    val limit: StateFlow<LimitNotice?> = _limit.asStateFlow()

    fun dismissLimit() { _limit.value = null }

    fun rename(name: String) {
        if (name.isBlank()) return
        val lib = repository.library.value
        val set = lib.setlist(setId) ?: return
        // o nome também vai no QR: um nome novo que faria o setlist passar do limite não é gravado
        if (QrPayload.fits(set, lib) && !QrPayload.fits(set, lib, name = name)) { _limit.value = LimitNotice.Name; return }
        repository.renameSetlist(setId, name)
    }

    fun delete() = repository.deleteSetlist(setId)

    fun showQr() {
        if (qrJob?.isActive == true || _qr.value != null) return   // toque duplo: um QR só
        val lib = repository.library.value
        val set = lib.setlist(setId) ?: return
        // setlist antigo que já passou do limite: avisa em vez de gerar um QR que a câmera não lê
        if (!QrPayload.fits(set, lib)) { _limit.value = LimitNotice.Songs; return }
        val payload = QrPayload.build(set, lib)
        val count = QrPayload.entries(set, lib).size
        qrJob = viewModelScope.launch {
            // setlist enorme pode não caber num QR: não pode derrubar o app
            val image = withContext(Dispatchers.Default) { runCatching { QrEncoder.encode(payload, 900).asImageBitmap() }.getOrNull() }
            if (image != null) _qr.value = QrUi(image, set.name, count, set.items.size - count)
        }
    }

    private var qrJob: Job? = null

    fun hideQr() { _qr.value = null }

    private companion object {
        const val JOIN_EVERY_MS = 15_000L
        const val JOIN_CHECKS = 40   // 10 min no máximo
    }
}
