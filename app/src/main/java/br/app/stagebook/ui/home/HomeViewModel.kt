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
class HomeViewModel @Inject constructor(private val repository: LibraryRepository) : ViewModel() {
    val essential: StateFlow<Boolean> = repository.library.map { it.look.essential }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.library.value.look.essential)

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
