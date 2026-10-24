package com.juliano.stagebook.ui.setlists

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.juliano.stagebook.data.LibraryRepository
import com.juliano.stagebook.domain.QrPayload
import com.juliano.stagebook.navigation.PickSongs
import com.juliano.stagebook.ui.songs.subtitle
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
import com.juliano.stagebook.ui.songs.typingDelay

@Immutable
data class PickRowUi(val id: String, val title: String, val subtitle: String, val inSet: Boolean, val selected: Boolean)

@Immutable
data class PickSongsUiState(val setName: String = "", val rows: List<PickRowUi> = emptyList(), val selectedCount: Int = 0)

@HiltViewModel
class PickSongsViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val setId = savedStateHandle.toRoute<PickSongs>().setId
    private val query = MutableStateFlow("")
    private val search = repository.songSearch
    // ordem em que foram marcadas = ordem em que entram no setlist
    private val selected = savedStateHandle.getStateFlow(KEY_SELECTED, ArrayList<String>())

    @OptIn(FlowPreview::class)
    val uiState: StateFlow<PickSongsUiState> = combine(repository.library, search, query.debounce(::typingDelay), selected) { lib, s, q, sel ->
        val set = lib.setlist(setId)
        PickSongsUiState(
            setName = set?.name.orEmpty(),
            rows = s.find(q)
                .map { PickRowUi(it.id, it.title, it.subtitle(), set?.items?.contains(it.id) == true, it.id in sel) },
            selectedCount = sel.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PickSongsUiState())

    fun onQueryChange(q: String) { query.value = q }

    private val _limit = MutableStateFlow(false)
    /** A música marcada passaria do que o QR aguenta: não entrou, e o aviso pede um setlist novo. */
    val limit: StateFlow<Boolean> = _limit

    /** Marca ou desmarca; true só quando a música acabou de ser marcada. */
    fun toggle(id: String): Boolean {
        val cur = selected.value
        if (id !in cur) {
            val lib = repository.library.value
            val set = lib.setlist(setId)
            // a conta a cada música marcada (as já no setlist + as marcadas + esta), pelo tamanho real do QR
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
