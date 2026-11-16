package br.app.stagebook.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import br.app.stagebook.domain.FollowWait

/** The song that waits for the follower to finish what they're doing, and why. */
@androidx.compose.runtime.Immutable
data class FollowPending(val target: String, val reason: FollowWait)

/**
 * Between the app's menus and windows and the follower's navigation: [menus] counts those open now ([FollowBusyWhile]),
 * [pending] is the song waiting (the strip tells it) and [go] opens it at once. [breakHeld] is the same for the break
 * shown over the stage the follower is already on (drawn by state, not by navigation, so the stage holds it).
 */
@Stable
class FollowGate {
    var menus by mutableIntStateOf(0)
    /**
     * Of [menus], the ones that are windows of their own (a popup, a dialog): those bring Android's navigation bar back, and
     * the strip is drawn above it. The library menu is not one: with only it open the strip stays where it is (it used to rise
     * over the menu's tabs, device test 16/11).
     */
    var windows by mutableIntStateOf(0)
    var pending by mutableStateOf<FollowPending?>(null)
    var go: () -> Unit = {}

    /** The leader's break (this stage has the song) waits behind an open window: the strip says so ([showBreak] shows it). */
    var breakHeld by mutableStateOf(false)
    var showBreak: () -> Unit = {}

    /**
     * A screen where "Go" can't just open the song on top of it (the editor, with what was typed): it leaves by itself,
     * asking first if it must, and then runs the [go] it is given. Null = "Go" opens the song at once.
     */
    var leaveGuard: ((go: () -> Unit) -> Unit)? = null
}

val LocalFollowGate = staticCompositionLocalOf { FollowGate() }

/** While [open], a menu or window of ours is open: the leader's song waits, and opens when it closes. */
@Composable
fun FollowBusyWhile(open: Boolean = true, window: Boolean = true) {
    val gate = LocalFollowGate.current
    DisposableEffect(gate, open, window) {
        if (open) { gate.menus++; if (window) gate.windows++ }
        onDispose { if (open) { gate.menus--; if (window) gate.windows-- } }
    }
}
