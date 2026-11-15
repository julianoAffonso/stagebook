package br.app.stagebook.ui.home

import androidx.lifecycle.ViewModel
import br.app.stagebook.data.LastStage
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.SetlistShowStore
import br.app.stagebook.domain.SetlistResume
import br.app.stagebook.navigation.Stage
import br.app.stagebook.navigation.lastStageRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

/** What the app opens on at launch: the stage that was open last (see [lastStageRoute]). */
@HiltViewModel
class StartViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val last: LastStage,
    private val show: SetlistShowStore,
) : ViewModel() {
    /** The library has been read: only then is "was it deleted?" a real answer. */
    val loaded: StateFlow<Boolean> = repository.loaded

    /** The library drawer is pinned beside the stage (where the screen is wide). */
    val pinned: StateFlow<Boolean> = repository.library.map { it.look.drawerPinned }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.library.value.look.drawerPinned)

    fun togglePin() = repository.updateLook { it.copy(drawerPinned = !it.drawerPinned) }

    fun route(): Stage? = lastStageRoute(last.place(), repository.library.value) { id ->
        val set = repository.library.value.setlist(id)
        if (set == null) br.app.stagebook.domain.StageStart(0, false)
        else SetlistResume.at(id, set.items, set.breaks, show.marks.value[id], show.breaks.value, show.today())
    }
}
