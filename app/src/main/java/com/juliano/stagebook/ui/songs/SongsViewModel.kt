package com.juliano.stagebook.ui.songs

import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.channels.Channel
import com.juliano.stagebook.data.BandLink
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.graphics.asImageBitmap
import com.juliano.stagebook.domain.ShareCode
import com.juliano.stagebook.domain.QrEncoder
import com.juliano.stagebook.ui.share.CloudUi
import com.juliano.stagebook.data.SetlistCloud
import com.juliano.stagebook.data.Connectivity
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juliano.stagebook.data.LibraryRepository
import com.juliano.stagebook.data.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map

@Immutable
data class SongRowUi(val id: String, val title: String, val subtitle: String)

@Immutable
data class SongsUiState(
    val loading: Boolean = true,
    val songs: List<SongRowUi> = emptyList(),
    val total: Int = 0,
)

/** Espera a pessoa parar de digitar antes de filtrar; limpar a busca é na hora. */
fun typingDelay(q: String): Long = if (q.isEmpty()) 0L else 150L

fun Song.subtitle(): String = listOf(author, year).filter { it.isNotBlank() }.joinToString(" · ") { it.trim() }

@HiltViewModel
class SongsViewModel @Inject constructor(
    private val repository: LibraryRepository,
    connectivity: Connectivity,
    private val setlistCloud: SetlistCloud,
    private val bandLink: BandLink,
) : ViewModel() {
    private val _cloud = MutableStateFlow<CloudUi?>(null)
    val cloud: StateFlow<CloudUi?> = _cloud

    /** Manda as músicas escolhidas pela internet (sem setlist); o código aparece num diálogo. */
    fun shareSongs(ids: List<String>) {
        if (_cloud.value == CloudUi.Sending || ids.isEmpty()) return
        _cloud.value = CloudUi.Sending
        viewModelScope.launch {
            val sent = setlistCloud.sendSongs(ids)
            val qr = sent?.let { s ->
                withContext(Dispatchers.Default) { runCatching { QrEncoder.encode(ShareCode.qrText(s.code).toByteArray(), 600).asImageBitmap() }.getOrNull() }
            }
            _cloud.value = sent?.let { CloudUi.Sent(it.code, "", it.songs, it.skipped, qr) } ?: CloudUi.Failed
        }
    }

    fun dismissCloud() { _cloud.value = null }

    /** Quantos da banda estão na rede agora ("Banda na rede (N)" só aparece com alguém). */
    val bandOnline: StateFlow<Int> = bandLink.onlineMembers.map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val bandSent = Channel<Int>(Channel.BUFFERED)
    /** Para quantos aparelhos da banda chegou o último envio (0 = ninguém recebeu). */
    val bandResult = bandSent.receiveAsFlow()

    /** A folha Compartilhar abriu/fechou: liga/desliga a procura da banda. */
    fun bandSearch(on: Boolean) = bandLink.setScreenOpen(on)

    /** Manda as músicas escolhidas para todos da banda na rede agora. */
    fun sendBand(ids: List<String>) {
        viewModelScope.launch {
            val pkg = setlistCloud.songsPackage(ids) ?: return@launch
            bandSent.send(bandLink.send(pkg.first, pkg.second))
        }
    }

    /** Importar precisa de internet: o botão fica indisponível sem ela (só observa com a tela aberta). */
    val online: StateFlow<Boolean> = connectivity.online.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val query = MutableStateFlow("")
    private val search = repository.songSearch

    @OptIn(FlowPreview::class)
    val uiState: StateFlow<SongsUiState> = combine(repository.loaded, search, query.debounce(::typingDelay)) { loaded, s, q ->
        val songs = s.find(q)
        SongsUiState(
            loading = !loaded,
            songs = songs.map { SongRowUi(it.id, it.title, it.subtitle()) },
            total = repository.library.value.songs.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SongsUiState())

    fun onQueryChange(q: String) { query.value = q }

    fun delete(id: String) = repository.deleteSong(id)
}
