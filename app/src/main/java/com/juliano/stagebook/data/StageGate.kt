package com.juliano.stagebook.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Palco aberto agora? Com ele aberto (show) nada de sincronia: nem download nem troca de letras. */
@Singleton
class StageGate @Inject constructor() {
    // contador: indo de um palco para outro, o novo entra antes de o velho sair
    private val open = MutableStateFlow(0)
    private val _onStage = MutableStateFlow(false)
    val onStage: StateFlow<Boolean> = _onStage.asStateFlow()
    // conta as saídas do palco: entrar e sair depressa não se perde (o onStage voltaria igual)
    private val _exits = MutableStateFlow(0)
    val exits: StateFlow<Int> = _exits.asStateFlow()

    fun enter() { open.update { it + 1 }; _onStage.value = open.value > 0 }
    fun leave() {
        open.update { (it - 1).coerceAtLeast(0) }
        _onStage.value = open.value > 0
        if (!_onStage.value) _exits.update { it + 1 }
    }
}
