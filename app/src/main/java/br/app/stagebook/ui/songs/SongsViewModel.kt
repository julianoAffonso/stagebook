package br.app.stagebook.ui.songs

import kotlinx.coroutines.flow.update
import br.app.stagebook.data.BandInternet
import br.app.stagebook.data.BandTrust
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.channels.Channel
import br.app.stagebook.data.BandLink
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.graphics.asImageBitmap
import br.app.stagebook.domain.ShareCode
import br.app.stagebook.domain.QrEncoder
import br.app.stagebook.ui.share.CloudUi
import br.app.stagebook.data.SetlistCloud
import br.app.stagebook.data.Connectivity
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.Song
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

/** Waits for the person to stop typing before filtering; clearing the search is immediate. */
fun typingDelay(q: String): Long = if (q.isEmpty()) 0L else 150L

fun Song.subtitle(): String = listOf(author, year).filter { it.isNotBlank() }.joinToString(" · ") { it.trim() }

@HiltViewModel
class SongsViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val connectivity: Connectivity,
    private val setlistCloud: SetlistCloud,
    private val bandLink: BandLink,
    private val bandTrust: BandTrust,
    private val bandInternet: BandInternet,
) : ViewModel() {
    val internetOn: StateFlow<Boolean> = repository.library.map { it.look.internetOn }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.library.value.look.internetOn)
    private val internet = combine(connectivity.online, internetOn) { a, b -> a && b }   // modo essencial (T19)
    private fun connectivityNow() = connectivity.isOnline()
    private val _cloud = MutableStateFlow<CloudUi?>(null)
    val cloud: StateFlow<CloudUi?> = _cloud

    /** Sends the chosen songs over the internet (no setlist); the code shows in a dialog. */
    fun shareSongs(ids: List<String>) {
        if (_cloud.value == CloudUi.Sending || ids.isEmpty()) return
        _cloud.value = CloudUi.Sending
        viewModelScope.launch {
            val sent = setlistCloud.sendSongs(ids)
            val qr = sent?.let { s ->
                withContext(Dispatchers.Default) { runCatching { QrEncoder.encode(ShareCode.qrText(s.code).toByteArray(), 600).asImageBitmap() }.getOrNull() }
            }
            sent?.let { bandInternet.remember(it.code); watchJoins(it.code) }
            _cloud.value = sent?.let { CloudUi.Sent(it.code, "", it.songs, it.skipped, qr) } ?: CloudUi.Failed
        }
    }

    fun dismissCloud() { _cloud.value = null; joinsJob?.cancel() }

    private var joinsJob: kotlinx.coroutines.Job? = null

    // with the words dialog open, checks now and then who accepted (they join the band); once closed, it stops
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
     * How many band members we can send to right now ("Send to the band (N)"): all of them if someone is on Wi-Fi or
     * there's internet; 0 = the option doesn't show.
     */
    val bandOnline: StateFlow<Int> = combine(bandTrust.members, bandLink.onlineMembers, internet) { members, lan, net ->
        if (members.isNotEmpty() && (lan.isNotEmpty() || net)) members.size else 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val bandSent = Channel<Int>(Channel.BUFFERED)
    /** How many band devices got the last send (0 = nobody got it). */
    val bandResult = bandSent.receiveAsFlow()

    /** The Share sheet opened or closed: turns the band search on or off. */
    fun bandSearch(on: Boolean) = bandLink.setScreenOpen(on)

    /** Sends the chosen songs to everyone in the band on the network now. */
    fun sendBand(ids: List<String>) {
        viewModelScope.launch {
            val pkg = setlistCloud.songsPackage(ids) ?: return@launch
            // nearby ones (Wi-Fi) right away; the others over the internet, in each pair's inbox
            val lan = bandLink.send(pkg.first, pkg.second)
            val net = if (connectivityNow()) bandInternet.sendToBand(pkg.first, pkg.second, lan) else 0
            bandSent.send(lan.size + net)
        }
    }

    /** Import needs internet: the button is disabled without it (only watched while the screen is open). */
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

    private companion object {
        const val JOIN_EVERY_MS = 15_000L
        const val JOIN_CHECKS = 40   // 10 min at most
    }
}
