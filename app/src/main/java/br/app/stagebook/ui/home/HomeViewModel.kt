package br.app.stagebook.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.Look
import br.app.stagebook.data.model.anyOff
import br.app.stagebook.data.model.essential
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** What Home needs besides the band inbox: the essential mode alert (T19). */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val webEditor: br.app.stagebook.data.web.WebEditorServer,
) : ViewModel() {
    /** The web editor's address while it's on ("" = off): the tabs show it's in use, with a way to close it. */
    val webAddress: StateFlow<String> = webEditor.address

    /** Closes the web editor (the same as its switch in Settings). */
    fun closeWebEditor() = repository.updateLook { it.copy(webEditorOn = false) }

    /**
     * The tab the app opens on: Setlists, or Songs while there's no setlist yet (user, 31/10). Null until the library has
     * loaded, so the screen doesn't show one tab and jump to the other.
     */
    val startTab: StateFlow<HomeTab?> = combine(repository.loaded, repository.library) { loaded, lib -> startOf(loaded, lib.sets.isEmpty()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, startOf(repository.loaded.value, repository.library.value.sets.isEmpty()))

    val essential: StateFlow<Boolean> = repository.library.map { it.look.essential }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.library.value.look.essential)

    /** Some part is off but not all: the tabs show the quiet leaf that lists what's off (user, 08/11). */
    val partial: StateFlow<Boolean> = repository.library.map { it.look.anyOff && !it.look.essential }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The switches, for the list of what's off. */
    val look: StateFlow<Look> = repository.library.map { it.look }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.library.value.look)

    /**
     * Alert on open when some part is off (what's off comes in the Look; null = no alert): once per launch, until
     * "Don't show again".
     */
    val alert: StateFlow<Look?> = combine(repository.library, alerted) { lib, seen -> lib.look.takeIf { it.anyOff && !it.essentialAlertOff && !seen } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun alertSeen(neverAgain: Boolean) {
        alerted.value = true
        if (neverAgain) repository.updateLook { it.copy(essentialAlertOff = true) }
    }

    private companion object {
        val alerted = MutableStateFlow(false)   // per app launch (process): going back to Home doesn't repeat it
    }

    /** "Sync with the band" uses the local network and the internet: hidden when both are off. */
    val canSync: StateFlow<Boolean> = repository.library.map { it.look.bandOn || it.look.internetOn }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
}

internal fun startOf(loaded: Boolean, noSetlists: Boolean): HomeTab? = if (!loaded) null else if (noSetlists) HomeTab.SONGS else HomeTab.SETLISTS
