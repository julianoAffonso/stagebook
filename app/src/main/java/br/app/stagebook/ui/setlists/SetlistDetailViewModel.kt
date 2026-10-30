package br.app.stagebook.ui.setlists

import kotlinx.coroutines.flow.update
import br.app.stagebook.data.BandInternet
import br.app.stagebook.data.BandTrust
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.channels.Channel
import br.app.stagebook.data.BandLink
import br.app.stagebook.ui.share.CloudUi
import br.app.stagebook.domain.ShareCode
import br.app.stagebook.data.Connectivity
import br.app.stagebook.data.SetlistCloud
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.domain.QrEncoder
import br.app.stagebook.domain.QrPayload
import br.app.stagebook.navigation.SetlistDetail
import br.app.stagebook.ui.songs.subtitle
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

/** `key` is stable when reordering (the same song can appear twice in old setlists). */
@Immutable
data class SetItemUi(val key: String, val songId: String, val title: String, val subtitle: String, val missing: Boolean)

@Immutable
data class SetlistDetailUiState(
    val loading: Boolean = true,
    val exists: Boolean = true,
    val name: String = "",
    val items: List<SetItemUi> = emptyList(),
)

/** QR ready: the image and how many songs go in it (the ones removed from the library don't). */
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
    private val conduct: br.app.stagebook.data.BandConduct,
) : ViewModel() {
    /** "Lead the band": sends the setlist (each one says what's missing) and the leader goes to the stage. */
    fun conductBand() {
        val lib = repository.library.value
        val set = lib.setlist(setId) ?: return
        val songs = set.items.mapNotNull(lib::song).map { br.app.stagebook.domain.Conduct.Item(it.title, it.author) }
        conduct.start(setId, set.name, songs)
    }

    val internetOn: StateFlow<Boolean> = repository.library.map { it.look.internetOn }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.library.value.look.internetOn)
    private val internet = combine(connectivity.online, internetOn) { a, b -> a && b }   // modo essencial (T19)
    private fun connectivityNow() = connectivity.isOnline()
    val setId = savedStateHandle.toRoute<SetlistDetail>().setId

    /** Internet now? "Code over the internet" only shows with it (only watched while the screen is open). */
    val online: StateFlow<Boolean> = connectivity.online.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _cloud = MutableStateFlow<CloudUi?>(null)
    val cloud: StateFlow<CloudUi?> = _cloud.asStateFlow()

    /** Sends the setlist over the internet (with the songs, if [withSongs]); the code shows in a dialog. */
    fun sendInternet(withSongs: Boolean) {
        if (_cloud.value == CloudUi.Sending) return
        _cloud.value = CloudUi.Sending
        viewModelScope.launch {
            val name = repository.library.value.setlist(setId)?.name.orEmpty()
            val sent = setlistCloud.send(setId, withSongs)
            // the code's QR: whoever is next to you takes a photo or scans it with "Scan QR"
            val qr = sent?.let { s ->
                withContext(Dispatchers.Default) { runCatching { QrEncoder.encode(ShareCode.qrText(s.code).toByteArray(), 600).asImageBitmap() }.getOrNull() }
            }
            sent?.let { bandInternet.remember(it.code); watchJoins(it.code) }
            _cloud.value = sent?.let { CloudUi.Sent(it.code, name, it.songs, it.skipped, qr) } ?: CloudUi.Failed
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

    /** Sends the setlist (with the songs, if [withSongs]) to everyone in the band on the network now. */
    fun sendBand(withSongs: Boolean) {
        viewModelScope.launch {
            val pkg = setlistCloud.setlistPackage(setId, withSongs) ?: return@launch
            // nearby ones (Wi-Fi) right away; the others over the internet, in each pair's inbox
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

    /** QR limit notice: [LimitNotice.Songs] (doesn't fit to send) or [LimitNotice.Name] (name too long). */
    enum class LimitNotice { Songs, Name }

    private val _limit = MutableStateFlow<LimitNotice?>(null)
    val limit: StateFlow<LimitNotice?> = _limit.asStateFlow()

    fun dismissLimit() { _limit.value = null }

    fun rename(name: String) {
        if (name.isBlank()) return
        val lib = repository.library.value
        val set = lib.setlist(setId) ?: return
        // the name also goes in the QR: a new name that would push the setlist over the limit isn't saved
        if (QrPayload.fits(set, lib) && !QrPayload.fits(set, lib, name = name)) { _limit.value = LimitNotice.Name; return }
        repository.renameSetlist(setId, name)
    }

    fun delete() = repository.deleteSetlist(setId)

    fun showQr() {
        if (qrJob?.isActive == true || _qr.value != null) return   // double tap: a single QR
        val lib = repository.library.value
        val set = lib.setlist(setId) ?: return
        // an old setlist already over the limit: warns instead of making a QR the camera can't read
        if (!QrPayload.fits(set, lib)) { _limit.value = LimitNotice.Songs; return }
        val payload = QrPayload.build(set, lib)
        val count = QrPayload.entries(set, lib).size
        qrJob = viewModelScope.launch {
            // a huge setlist may not fit in a QR: it can't crash the app
            val image = withContext(Dispatchers.Default) { runCatching { QrEncoder.encode(payload, 900).asImageBitmap() }.getOrNull() }
            if (image != null) _qr.value = QrUi(image, set.name, count, set.items.size - count)
        }
    }

    private var qrJob: Job? = null

    fun hideQr() { _qr.value = null }

    private companion object {
        const val JOIN_EVERY_MS = 15_000L
        const val JOIN_CHECKS = 40   // 10 min at most
    }
}
