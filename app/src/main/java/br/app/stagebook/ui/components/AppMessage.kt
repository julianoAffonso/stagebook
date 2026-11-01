package br.app.stagebook.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

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

/**
 * How far the message sits above the bottom of the screen: the phone's tab bar sets its height here, so the message
 * shows above the tabs instead of covering them (device test B-29). Zero on screens without a bottom bar.
 */
class MessageLift {
    var bottom by androidx.compose.runtime.mutableStateOf(0.dp)
}

val LocalMessageLift = staticCompositionLocalOf { MessageLift() }
