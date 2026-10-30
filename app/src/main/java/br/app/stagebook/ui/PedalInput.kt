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
 * Apertos do pedal chegam na Activity (teclas do pedal Bluetooth comum e mensagens do pedal MIDI) e vão
 * para a tela que estiver ouvindo por último (palco ou teste do pedal). Não depende de foco: o pedal
 * funciona mesmo sem tocar na tela antes.
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

/** Pedal, teclado Bluetooth etc.; deixa de fora os botões do próprio aparelho e os virtuais. */
fun isExternal(d: InputDevice): Boolean =
    !d.isVirtual && (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) d.isExternal else d.name?.contains("gpio", ignoreCase = true) != true)

val LocalPedalInput = staticCompositionLocalOf { PedalInput() }

/** Enquanto esta tela estiver na composição, recebe os apertos; devolve true para consumir. */
@Composable
fun PedalHandler(onPress: (PedalPress) -> Boolean) {
    val input = LocalPedalInput.current
    val current by rememberUpdatedState(onPress)
    DisposableEffect(input) {
        val unregister = input.register { current(it) }
        onDispose { unregister() }
    }
}
