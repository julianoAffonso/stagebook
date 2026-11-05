package br.app.stagebook.ui.setlists

import br.app.stagebook.ui.songs.typingDelay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.app.stagebook.data.Connectivity
import br.app.stagebook.data.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import br.app.stagebook.domain.QrPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@Immutable
data class SetlistRowUi(val id: String, val name: String, val count: Int)

@Immutable
data class SetlistsUiState(val loading: Boolean = true, val sets: List<SetlistRowUi> = emptyList(), val abc: Boolean = false, val total: Int = sets.size, val query: String = "")

/**
 * The Setlists tab's order: as stored (newest first) or A to Z, ignoring case and accents with the app's one text
 * key, as tags and songs are sorted (a Collator here was a second way of doing it; audit 03/11).
 */
fun setlistOrder(sets: List<SetlistRowUi>, abc: Boolean): List<SetlistRowUi> =
    if (abc) br.app.stagebook.domain.TextKey.sorted(sets) { it.name } else sets

/** The setlists whose name has the search, ignoring case and accents as the song search does. */
fun setlistSearch(sets: List<SetlistRowUi>, query: String): List<SetlistRowUi> {
    val key = br.app.stagebook.domain.TextKey.of(query)
    return if (key.isEmpty()) sets else sets.filter { br.app.stagebook.domain.TextKey.of(it.name).contains(key) }
}

@HiltViewModel
class SetlistsViewModel @Inject constructor(
    private val repository: LibraryRepository,
    connectivity: Connectivity,
    private val setlistCloud: br.app.stagebook.data.SetlistCloud,
    bandLink: br.app.stagebook.data.BandLink,
    bandTrust: br.app.stagebook.data.BandTrust,
    bandInternet: br.app.stagebook.data.BandInternet,
) : ViewModel() {
    // sharing several setlists at once (user, 01/11): the same sheet, code and band send as inside a setlist
    private val share = br.app.stagebook.ui.share.ShareSender(viewModelScope, repository, connectivity, bandLink, bandTrust, bandInternet)
    val cloud: StateFlow<br.app.stagebook.ui.share.CloudUi?> = share.cloud
    val bandOnline: StateFlow<Int> = share.bandOnline
    val bandResult = share.bandResult

    /** How many different songs the chosen setlists have (the same song in two counts once). */
    fun songsIn(ids: List<String>): Int = ids.flatMap { repository.library.value.setlist(it)?.items.orEmpty() }.distinct().size

    /** One code with all the chosen setlists (and their songs, if [withSongs]). */
    fun shareInternet(ids: List<String>, withSongs: Boolean) {
        val names = ids.mapNotNull { repository.library.value.setlist(it)?.name }
        share.sendCode(names.joinToString(", ")) { setlistCloud.sendMany(ids, withSongs) }
    }

    /** All the chosen setlists to everyone in the band on the network, one tap. */
    fun sendBand(ids: List<String>, withSongs: Boolean) = share.sendBandMany { ids.mapNotNull { setlistCloud.setlistPackage(it, withSongs) } }

    fun bandSearch(on: Boolean) = share.bandSearch(on)
    fun dismissCloud() = share.dismissCloud()

    /** "Type code" only with internet (only watched while the tab is open). */
    val online: StateFlow<Boolean> = connectivity.online.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val localNet: StateFlow<Boolean> = connectivity.localNet.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val internetOn: StateFlow<Boolean> = repository.library.map { it.look.internetOn }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.library.value.look.internetOn)

    private val query = MutableStateFlow("")

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val uiState: StateFlow<SetlistsUiState> = combine(repository.loaded, repository.library, query.debounce(::typingDelay)) { loaded, lib, q ->
        val rows = setlistOrder(lib.sets.map { SetlistRowUi(it.id, it.name, it.items.size) }, lib.look.setsAbc)
        SetlistsUiState(!loaded, setlistSearch(rows, q), lib.look.setsAbc, lib.sets.size, q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetlistsUiState())

    /** Empty name = current date and time. Returns the id to open the new setlist. */
    fun create(name: String): String =
        repository.createSetlist(name.ifBlank { br.app.stagebook.data.model.Setlist.defaultName() })

    fun delete(id: String) = repository.deleteSetlist(id)

    fun onQueryChange(q: String) { query.value = q }

    fun sortAbc(abc: Boolean) = repository.updateLook { it.copy(setsAbc = abc) }

    private val _nameLimit = MutableStateFlow(false)
    /** The new name would push the setlist over the QR limit (same notice as inside the setlist). */
    val nameLimit: StateFlow<Boolean> = _nameLimit.asStateFlow()

    fun dismissNameLimit() { _nameLimit.value = false }

    fun rename(id: String, name: String) {
        if (!repository.renameIfFits(id, name)) _nameLimit.value = true
    }
}

/**
 * Renames a setlist unless the new name would push it over what the QR holds (the name goes in the QR too); false =
 * not saved for that reason. A blank name or a setlist that's gone change nothing.
 */
internal fun br.app.stagebook.data.LibraryRepository.renameIfFits(id: String, name: String): Boolean {
    if (name.isBlank()) return true
    val lib = library.value
    val set = lib.setlist(id) ?: return true
    if (QrPayload.fits(set, lib) && !QrPayload.fits(set, lib, name = name)) return false
    renameSetlist(id, name)
    return true
}
