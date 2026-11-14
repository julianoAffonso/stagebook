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

/**
 * A screen that can pull a band follower to the leader's song (the setlist) says here when it's busy (a dialog open,
 * a drag going on): the song then waits and opens when it's done, so a rename or a reorder isn't thrown away (audit
 * 02/11).
 */
val LocalFollowHold = staticCompositionLocalOf<MutableState<Boolean>> { mutableStateOf(false) }

/**
 * While [open], this screen is one where a band follower is in the middle of something and isn't taken to the stage: the
 * song waits, the strip says so, and it opens when the screen is left (or "Go"). The Settings tab and the screens inside
 * it say so for Home; the setlist screen says it for itself when busy.
 */
@Composable
fun FollowHoldWhile(open: Boolean) {
    val hold = LocalFollowHold.current
    DisposableEffect(hold, open) {
        hold.value = open
        onDispose { hold.value = false }
    }
}

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
fun FollowBusyWhile(open: Boolean = true) {
    val gate = LocalFollowGate.current
    DisposableEffect(gate, open) {
        if (open) gate.menus++
        onDispose { if (open) gate.menus-- }
    }
}
