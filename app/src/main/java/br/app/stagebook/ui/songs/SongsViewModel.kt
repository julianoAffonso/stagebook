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
data class SongRowUi(val id: String, val title: String, val subtitle: String, val tags: List<String> = emptyList())

@Immutable
data class SongsUiState(
    val loading: Boolean = true,
    val songs: List<SongRowUi> = emptyList(),
    val total: Int = 0,
    val tags: List<String> = emptyList(),        // every tag in the library (the filter's options)
    val chosenTags: Set<String> = emptySet(),
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
    // the Share sheet's internet code and band send, shared with the setlist screen
    private val share = br.app.stagebook.ui.share.ShareSender(viewModelScope, repository, connectivity, bandLink, bandTrust, bandInternet)
    val internetOn: StateFlow<Boolean> = share.internetOn
    val cloud: StateFlow<CloudUi?> = share.cloud
    val bandOnline: StateFlow<Int> = share.bandOnline
    /** How many band devices got the last send (0 = nobody got it). */
    val bandResult = share.bandResult

    /** Sends the chosen songs over the internet (no setlist); the code shows in a dialog. */
    fun shareSongs(ids: List<String>) { if (ids.isNotEmpty()) share.sendCode("") { setlistCloud.sendSongs(ids) } }

    fun dismissCloud() = share.dismissCloud()

    /** The Share sheet opened or closed: turns the band search on or off. */
    fun bandSearch(on: Boolean) = share.bandSearch(on)

    /** Sends the chosen songs to everyone in the band on the network now. */
    fun sendBand(ids: List<String>) = share.sendBand { setlistCloud.songsPackage(ids) }

    /** Import needs internet: the button is disabled without it (only watched while the screen is open). */
    val online: StateFlow<Boolean> = connectivity.online.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val query = MutableStateFlow("")
    private val search = repository.songSearch
    private val chosenTags = MutableStateFlow(emptySet<String>())

    @OptIn(FlowPreview::class)
    val uiState: StateFlow<SongsUiState> = combine(repository.loaded, search, query.debounce(::typingDelay), chosenTags) { loaded, s, q, chosen ->
        val all = br.app.stagebook.domain.Tags.all(repository.library.value.songs)
        // a tag that no song has any more (taken off everywhere) leaves the filter
        val kept = chosen.filter { c -> all.any { it.equals(c, true) } }.toSet()
        val songs = br.app.stagebook.domain.Tags.filter(s.find(q), kept) { it.tags }
        SongsUiState(
            loading = !loaded,
            songs = songs.map { SongRowUi(it.id, it.title, it.subtitle(), it.tags) },
            total = repository.library.value.songs.size,
            tags = all,
            chosenTags = kept,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SongsUiState())

    fun onQueryChange(q: String) { query.value = q }

    fun onTagsChange(tags: Set<String>) { chosenTags.value = tags }

    /** Adds and takes off tags on the chosen songs. */
    fun setTags(ids: List<String>, add: List<String>, remove: List<String>) = repository.updateSongs(ids) { song ->
        var t = song.tags
        remove.forEach { t = br.app.stagebook.domain.Tags.remove(t, it) }
        add.forEach { t = br.app.stagebook.domain.Tags.add(t, br.app.stagebook.domain.Tags.clean(it)) }
        song.copy(tags = t)
    }

    /**
     * "Split Brazilian and international": the songs with neither tag yet, by the lyrics language (the ones it can't
     * tell stay out). [preview] counts without saving, for the confirmation.
     */
    fun autoTags(national: String, international: String, preview: Boolean): Pair<Int, Int> {
        val pending = repository.library.value.songs
            .filter { !br.app.stagebook.domain.Tags.has(it, national) && !br.app.stagebook.domain.Tags.has(it, international) }
            .mapNotNull { s -> br.app.stagebook.domain.LyricsLanguage.isPortuguese(s.text)?.let { s.id to it } }
        val br = pending.filter { it.second }.map { it.first }
        val other = pending.filterNot { it.second }.map { it.first }
        if (!preview) {
            setTags(br, listOf(national), emptyList())
            setTags(other, listOf(international), emptyList())
        }
        return br.size to other.size
    }

    fun delete(id: String) = repository.deleteSong(id)
}
