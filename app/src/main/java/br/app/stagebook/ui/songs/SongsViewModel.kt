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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import br.app.stagebook.domain.SongGroups

@Immutable
data class SongRowUi(
    val id: String,
    val title: String,
    val subtitle: String,   // the artist
    val tags: List<String> = emptyList(),
    val songKey: String = "",   // the song's key ("" = none written)
    val bpm: Int = 0,           // 0 = none
    val capo: Int = 0,
    val hasTrack: Boolean = false,
)

/** A letter of the A to Z index and where its first song is in [SongsUiState.songs]. */
@Immutable
data class SongGroupUi(val letter: String, val first: Int)

@Immutable
data class SongsUiState(
    val loading: Boolean = true,
    val songs: List<SongRowUi> = emptyList(),
    val total: Int = 0,
    val tags: List<String> = emptyList(),        // every tag in the library (the filter's options)
    val chosenTags: Set<String> = emptySet(),
    val sets: List<SetPick> = emptyList(),        // setlists to add a selection to
    val query: String = "",                       // the search these rows came from
    val groups: List<SongGroupUi> = emptyList(),  // the A to Z groups of these rows (none: a plain list)
)

@Immutable
data class SetPick(val id: String, val name: String)

/** Adding a selection to a setlist: added (how many, where) or over what the setlist's QR holds. */
sealed interface AddResult {
    data class Added(val count: Int, val set: String) : AddResult
    data object Limit : AddResult
    data object Gone : AddResult   // the setlist was deleted meanwhile (on another screen, or by the band)
}

/** Waits for the person to stop typing before filtering; clearing the search is immediate. */
fun typingDelay(q: String): Long = if (q.isEmpty()) 0L else 150L

/** Artist and year: under the song's name on stage. */
fun Song.subtitle(): String = listOf(author, year).filter { it.isNotBlank() }.joinToString(" · ") { it.trim() }

/** The artist alone: a list's rows leave the year out (user, 13/11). */
fun Song.artistLine(): String = author.trim()

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
    val localNet: StateFlow<Boolean> = connectivity.localNet.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val query = MutableStateFlow("")
    private val search = repository.songSearch
    private val chosenTags = MutableStateFlow(emptySet<String>())
    // the setlists for "Add to which setlist?": they change without the songs changing (audit 01/11)
    private val setPicks = repository.library.map { l -> l.sets.map { SetPick(it.id, it.name) } }.distinctUntilChanged()

    // key, tempo, capo and the index letter of every song, shared with the screen that picks songs for a setlist
    private val facts = RowFactsReader().of(repository.library.map { it.songs })

    @OptIn(FlowPreview::class)
    val uiState: StateFlow<SongsUiState> = combine(repository.loaded, search.combine(facts, ::Pair), query.debounce(::typingDelay), chosenTags, setPicks) { loaded, (s, known), q, chosen, sets ->
        val all = br.app.stagebook.domain.Tags.all(repository.library.value.songs)
        val kept = br.app.stagebook.domain.Tags.stillUsed(chosen, all)
        val rows = br.app.stagebook.domain.Tags.filter(s.find(q), kept) { it.tags }.map {
            val fact = known[it.id]
            // a song the search already has and the facts don't yet: its row comes bare and is redone right after
            SongRowUi(it.id, it.title, it.artistLine(), it.tags, fact?.meta?.key.orEmpty(), fact?.meta?.tempo ?: 0, fact?.meta?.capo ?: 0, fact?.hasTrack == true) to (fact?.letter ?: SongGroups.letter(it.title))
        }
        val grouped = SongGroups.group(rows) { it.second }
        SongsUiState(
            loading = !loaded,
            songs = grouped.rows.map { it.first },
            total = repository.library.value.songs.size,
            tags = all,
            chosenTags = kept,
            sets = sets,
            query = q,
            groups = grouped.groups.map { SongGroupUi(it.letter, it.first) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SongsUiState())

    /** The automatic profile imports lyrics or chords: the importer has to ask which before it opens a site. */
    fun asksKind(): Boolean = repository.library.value.look.autoProfile
    fun onQueryChange(q: String) { query.value = q }

    fun onTagsChange(tags: Set<String>) { chosenTags.value = tags }

    /** The tags of each chosen song (also the ones the search hides). */
    fun tagsOf(ids: List<String>): List<List<String>> = ids.mapNotNull { repository.song(it)?.tags }

    /** Adds and takes off tags on the chosen songs. */
    fun setTags(ids: List<String>, add: List<String>, remove: List<String>) =
        repository.updateSongs(ids) { song -> song.copy(tags = br.app.stagebook.domain.Tags.apply(song.tags, add, remove)) }



    fun deleteMany(ids: List<String>) = repository.deleteSongs(ids)

    /**
     * Adds the selection to a setlist ([setId]), or to a new one named [newName] (empty = date and time, as on the
     * Setlists tab). The ones already there stay where they are; it stops before going over what the QR can hold.
     */
    fun addToSetlist(ids: List<String>, setId: String?, newName: String): AddResult {
        val lib = repository.library.value
        val name = newName.ifBlank { br.app.stagebook.data.model.Setlist.defaultName() }
        val set = (if (setId != null) lib.setlist(setId) ?: return AddResult.Gone else null) ?: br.app.stagebook.data.model.Setlist(name = name)
        val extra = ids.filterNot { it in set.items }
        if (!br.app.stagebook.domain.QrPayload.fits(set, lib, extraIds = extra)) return AddResult.Limit
        val id = setId ?: repository.createSetlist(name)
        repository.addToSetlist(id, extra)
        return AddResult.Added(extra.size, set.name)
    }
}
