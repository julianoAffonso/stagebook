package br.app.stagebook.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The app's short message: a single snackbar at the bottom of the screen (above the "Lead the band" strip), in Google's
 * app pattern. Every screen shows messages here (never Toast): `LocalAppMessage.current.show(...)`.
 */
class AppMessage(val host: SnackbarHostState, private val scope: CoroutineScope) {
    fun show(text: String, long: Boolean = false, action: String? = null, onAction: () -> Unit = {}) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val result = host.showSnackbar(text, actionLabel = action, duration = if (long) SnackbarDuration.Long else SnackbarDuration.Short)
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) onAction()
        }
    }
}

@Composable
fun rememberAppMessage(): AppMessage {
    val scope = rememberCoroutineScope()
    return remember(scope) { AppMessage(SnackbarHostState(), scope) }
}

/** Without the app root (Preview, testing a component alone), the message doesn't show. */
val LocalAppMessage = staticCompositionLocalOf<AppMessage> {
    AppMessage(SnackbarHostState(), CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
}
