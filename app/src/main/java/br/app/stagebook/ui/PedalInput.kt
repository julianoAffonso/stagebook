package br.app.stagebook.ui

import android.content.Context
import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import android.view.ViewTreeObserver
import android.widget.FrameLayout
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

/**
 * The window's root: hands the keys to the pedal before the keyboard and before Android's touch mode. Right after a
 * touch on the screen, Android uses up the first arrow key to leave touch mode, so the first pedal press did nothing
 * (device test, 01/11). It holds focus itself, in touch mode too, so the keys always pass through it.
 *
 * And it takes focus back whenever the window is left with none: a text field that had it (the search, the editor) and
 * went away leaves nothing focused in touch mode, the keys stopped passing through here and the first press on the
 * next stage was used up again (two devices, 13/11).
 */
class PedalKeyRoot(context: Context, private val pedal: PedalInput) : FrameLayout(context) {
    init { isFocusableInTouchMode = true }

    private val keepFocus = ViewTreeObserver.OnGlobalFocusChangeListener { _, now -> if (now == null && !hasFocus()) requestFocus() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!hasFocus()) requestFocus()
        viewTreeObserver.addOnGlobalFocusChangeListener(keepFocus)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnGlobalFocusChangeListener(keepFocus)
        super.onDetachedFromWindow()
    }

    override fun dispatchKeyEventPreIme(event: KeyEvent): Boolean = pedal.dispatch(event) || super.dispatchKeyEventPreIme(event)
}

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
