package com.juliano.stagebook.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Aviso curto do app: um snackbar só, no rodapé da tela (acima da faixa do "Quem conduz"), no padrão
 * dos apps do Google. Toda tela avisa por aqui (nunca Toast): `LocalAppMessage.current.show(...)`.
 */
class AppMessage(val host: SnackbarHostState, private val scope: CoroutineScope) {
    fun show(text: String, long: Boolean = false) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            host.showSnackbar(text, duration = if (long) SnackbarDuration.Long else SnackbarDuration.Short)
        }
    }
}

@Composable
fun rememberAppMessage(): AppMessage {
    val scope = rememberCoroutineScope()
    return remember(scope) { AppMessage(SnackbarHostState(), scope) }
}

/** Sem a raiz do app (Preview, teste de um componente sozinho), o aviso não aparece. */
val LocalAppMessage = staticCompositionLocalOf<AppMessage> {
    AppMessage(SnackbarHostState(), CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
}
