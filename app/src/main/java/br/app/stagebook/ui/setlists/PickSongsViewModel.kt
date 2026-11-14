package br.app.stagebook.ui.setlists

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.domain.QrPayload
import br.app.stagebook.navigation.PickSongs
import br.app.stagebook.ui.songs.RowFactsReader
import br.app.stagebook.ui.songs.artistLine
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
import br.app.stagebook.ui.songs.typingDelay

@Immutable
data class PickRowUi(
    val id: String,
    val title: String,
    val subtitle: String,   // the artist
    val inSet: Boolean,
    val selected: Boolean,
    val songKey: String = "",   // the song's key ("" = none written)
    val bpm: Int = 0,           // 0 = none
    val capo: Int = 0,
    val hasTrack: Boolean = false,
)

@Immutable
data class PickSongsUiState(
    val setName: String = "",
    val rows: List<PickRowUi> = emptyList(),
    val selectedCount: Int = 0,
    val tags: List<String> = emptyList(),
    val chosenTags: Set<String> = emptySet(),
)

@HiltViewModel
class PickSongsViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val setId = savedStateHandle.toRoute<PickSongs>().setId
    private val query = MutableStateFlow("")
    private val search = repository.songSearch
    // order they were checked = order they go into the setlist
    private val selected = savedStateHandle.getStateFlow(KEY_SELECTED, ArrayList<String>())
    // building the setlist by tag: "only Brazilian songs" (user, 01/11)
    private val chosenTags = MutableStateFlow(emptySet<String>())

    // the rows' key, tempo, capo and backing track, read the same way as the song list's
    private val facts = RowFactsReader().of(repository.library.map { it.songs })

    // the search, tags and filter don't change when a song is checked: only the check marks are redone (audit 01/11)
    @OptIn(FlowPreview::class)
    private val filtered = combine(repository.library, search.combine(facts, ::Pair), query.debounce(::typingDelay), chosenTags) { lib, (s, known), q, chosen ->
        val set = lib.setlist(setId)
        val all = br.app.stagebook.domain.Tags.all(lib.songs)
        val kept = br.app.stagebook.domain.Tags.stillUsed(chosen, all)
        val rows = br.app.stagebook.domain.Tags.filter(s.find(q), kept) { it.tags }.map {
            val fact = known[it.id]
            // a song the search already has and the facts don't yet: its row comes bare and is redone right after
            PickRowUi(
                it.id, it.title, it.artistLine(), set?.items?.contains(it.id) == true, false,
                fact?.meta?.key.orEmpty(), fact?.meta?.tempo ?: 0, fact?.meta?.capo ?: 0, fact?.hasTrack == true,
            )
        }
        PickSongsUiState(setName = set?.name.orEmpty(), rows = rows, tags = all, chosenTags = kept)
    }

    val uiState: StateFlow<PickSongsUiState> = combine(filtered, selected) { f, sel ->
        val chosen = sel.toHashSet()
        f.copy(rows = f.rows.map { if (it.id in chosen) it.copy(selected = true) else it }, selectedCount = sel.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PickSongsUiState())

    fun onQueryChange(q: String) { query.value = q }

    fun onTagsChange(tags: Set<String>) { chosenTags.value = tags }

    private val _limit = MutableStateFlow(false)
    /** The checked song would go over what the QR can hold: it didn't go in, and the notice asks for a new setlist. */
    val limit: StateFlow<Boolean> = _limit

    /** Checks or unchecks; true only when the song was just checked. */
    fun toggle(id: String): Boolean {
        val cur = selected.value
        if (id !in cur) {
            val lib = repository.library.value
            val set = lib.setlist(setId)
            // the count for each checked song (those already in the setlist + checked + this one), by the real QR size
            if (set != null && !QrPayload.fits(set, lib, extraIds = (cur + id).filterNot { it in set.items })) {
                _limit.value = true
                return false
            }
        }
        savedStateHandle[KEY_SELECTED] = ArrayList(if (id in cur) cur - id else cur + id)
        return id !in cur
    }

    fun dismissLimit() { _limit.value = false }

    fun confirm() = repository.addToSetlist(setId, selected.value)

    private companion object {
        const val KEY_SELECTED = "selected"
    }
}
