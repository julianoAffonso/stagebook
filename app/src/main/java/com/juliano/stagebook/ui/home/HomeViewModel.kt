package com.juliano.stagebook.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juliano.stagebook.data.LibraryRepository
import com.juliano.stagebook.data.model.essential
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** O que o Início precisa além da caixa da banda: o aviso do modo essencial (T19). */
@HiltViewModel
class HomeViewModel @Inject constructor(private val repository: LibraryRepository) : ViewModel() {
    val essential: StateFlow<Boolean> = repository.library.map { it.look.essential }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.library.value.look.essential)

    /** Aviso ao abrir o app no modo essencial: uma vez por abertura, até marcar "Não mostrar de novo". */
    val alert: StateFlow<Boolean> = combine(repository.library, alerted) { lib, seen -> lib.look.essential && !lib.look.essentialAlertOff && !seen }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun alertSeen(neverAgain: Boolean) {
        alerted.value = true
        if (neverAgain) repository.updateLook { it.copy(essentialAlertOff = true) }
    }

    private companion object {
        val alerted = MutableStateFlow(false)   // por abertura do app (processo): voltar ao Início não repete
    }

    /** "Sincronizar com a banda" usa a rede local e a internet: some com as duas desligadas. */
    val canSync: StateFlow<Boolean> = repository.library.map { it.look.bandOn || it.look.internetOn }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
}
