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

/** The song that waits for the follower to finish what they're doing, and why. */
@androidx.compose.runtime.Immutable
data class FollowPending(val target: String, val reason: FollowWait)

/**
 * Between the app's menus and windows and the follower's navigation: [menus] counts those open now ([FollowBusyWhile]),
 * [pending] is the song waiting (the strip tells it) and [go] opens it at once.
 */
@Stable
class FollowGate {
    var menus by mutableIntStateOf(0)
    var pending by mutableStateOf<FollowPending?>(null)
    var go: () -> Unit = {}
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
