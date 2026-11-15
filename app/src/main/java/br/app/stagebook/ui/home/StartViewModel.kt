package br.app.stagebook.ui.home

import androidx.lifecycle.ViewModel
import br.app.stagebook.data.LastStage
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.SetlistShowStore
import br.app.stagebook.domain.SetlistResume
import br.app.stagebook.navigation.Stage
import br.app.stagebook.navigation.lastStageRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
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

    fun route(): Stage? = lastStageRoute(last.place(), repository.library.value) { id ->
        val set = repository.library.value.setlist(id)
        if (set == null) br.app.stagebook.domain.StageStart(0, false)
        else SetlistResume.at(id, set.items, set.breaks, show.marks.value[id], show.breaks.value, show.today())
    }
}
