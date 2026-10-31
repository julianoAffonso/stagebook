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

    // the Share sheet's internet code and band send, shared with the songs screen
    private val share = br.app.stagebook.ui.share.ShareSender(viewModelScope, repository, connectivity, bandLink, bandTrust, bandInternet)
    val internetOn: StateFlow<Boolean> = share.internetOn
    val setId = savedStateHandle.toRoute<SetlistDetail>().setId

    /** Internet now? "Code over the internet" only shows with it (only watched while the screen is open). */
    val online: StateFlow<Boolean> = connectivity.online.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val cloud: StateFlow<CloudUi?> = share.cloud
    val bandOnline: StateFlow<Int> = share.bandOnline
    /** How many band devices got the last send (0 = nobody got it). */
    val bandResult = share.bandResult

    /** Sends the setlist over the internet (with the songs, if [withSongs]); the code shows in a dialog. */
    fun sendInternet(withSongs: Boolean) =
        share.sendCode(repository.library.value.setlist(setId)?.name.orEmpty()) { setlistCloud.send(setId, withSongs) }

    fun dismissCloud() = share.dismissCloud()

    /** The Share sheet opened or closed: turns the band search on or off. */
    fun bandSearch(on: Boolean) = share.bandSearch(on)

    /** Sends the setlist (with the songs, if [withSongs]) to everyone in the band on the network now. */
    fun sendBand(withSongs: Boolean) = share.sendBand { setlistCloud.setlistPackage(setId, withSongs) }

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
}
