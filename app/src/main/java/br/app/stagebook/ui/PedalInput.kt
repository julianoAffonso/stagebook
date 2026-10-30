package br.app.stagebook.ui

import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import br.app.stagebook.domain.PedalPress

/**
 * Pedal presses reach the Activity (regular Bluetooth pedal keys and MIDI pedal messages) and go to the screen that
 * started listening last (stage or pedal test). Doesn't depend on focus: the pedal works without tapping the screen
 * first.
 */
class PedalInput {
    private val handlers = ArrayList<(PedalPress) -> Boolean>()

    fun dispatch(press: PedalPress): Boolean = handlers.lastOrNull()?.invoke(press) ?: false

    fun dispatch(event: KeyEvent): Boolean = dispatch(
        PedalPress(
            code = event.keyCode,
            down = event.action == KeyEvent.ACTION_DOWN,
            repeat = event.repeatCount,
            device = event.device?.takeIf(::isExternal)?.name.orEmpty(),
            name = KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_"),
        ),
    )

    fun register(handler: (PedalPress) -> Boolean): () -> Unit {
        handlers += handler
        return { handlers -= handler }
    }
}

/** Pedal, Bluetooth keyboard and so on; leaves out the device's own and virtual buttons. */
fun isExternal(d: InputDevice): Boolean =
    !d.isVirtual && (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) d.isExternal else d.name?.contains("gpio", ignoreCase = true) != true)

val LocalPedalInput = staticCompositionLocalOf { PedalInput() }

/** While this screen is in the composition, it gets the presses; return true to consume. */
@Composable
fun PedalHandler(onPress: (PedalPress) -> Boolean) {
    val input = LocalPedalInput.current
    val current by rememberUpdatedState(onPress)
    DisposableEffect(input) {
        val unregister = input.register { current(it) }
        onDispose { unregister() }
    }
}
