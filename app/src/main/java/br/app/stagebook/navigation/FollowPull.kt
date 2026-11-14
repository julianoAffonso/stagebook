package br.app.stagebook.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import br.app.stagebook.data.BandConduct
import br.app.stagebook.domain.FollowWait
import br.app.stagebook.domain.PendingFollow
import br.app.stagebook.domain.followBlock
import br.app.stagebook.ui.components.FollowGate
import br.app.stagebook.ui.components.FollowPending
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

// screens the leader's song can take the follower away from: nothing is being typed or chosen there. Home's Songs and
// Setlists tabs do; its Settings tab (and the screens inside it) waits, like the setlist when busy (FollowHoldWhile)
internal fun NavDestination.pullsToStage() = hasRoute<Home>() || hasRoute<Stage>() || hasRoute<SetlistDetail>()

internal fun stageFor(target: String) = if (target == BandConduct.BREAK_ONLY) Stage(leaderBreak = true) else Stage(songId = target)

/**
 * The leader changed songs: the follower's stage opens on it (replacing the open stage, without stacking), unless the
 * person is in the middle of something: a screen with typing or choosing (editor, importer, a setlist being changed), a
 * menu or window open, the keyboard open. Then the song waits (only the latest, with no time limit, nothing running
 * meanwhile) and opens by itself when they finish, if the leader is still on it; the strip says so and its "Go" opens it
 * at once ([gate]). Back from the stage returns to the screen they were on.
 *
 * The open menu or window is known from [FollowGate.menus] (our own menus and windows say so, see FollowBusyWhile) and
 * from the window losing focus (a menu or a window takes it from the main one); the keyboard from the window insets.
 * Just looking at a setlist does pull (the S26 sat on the setlist while the band played, 02/11).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FollowToStage(
    nav: NavController, followSongs: Flow<String>, followingSong: StateFlow<String?>, gate: FollowGate, followHold: MutableState<Boolean>,
) {
    val pendingFollow = remember { PendingFollow() }
    val entry by nav.currentBackStackEntryAsState()
    val focused = LocalWindowInfo.current.isWindowFocused
    val keyboard = WindowInsets.isImeVisible
    val here = entry?.destination
    val wait: FollowWait? = here?.let {
        // on the stage only our own menus and windows hold the song: the window losing focus there (split screen, the
        // shade) must not stop a follower for good
        followBlock(it.pullsToStage(), (it.hasRoute<SetlistDetail>() || it.hasRoute<Home>()) && followHold.value, it.hasRoute<Stage>(), gate.menus > 0 || (!focused && !it.hasRoute<Stage>()), keyboard)
    }
    val waitNow by rememberUpdatedState(wait)
    fun sync() { gate.pending = pendingFollow.waiting?.let { FollowPending(it, pendingFollow.reason ?: FollowWait.SCREEN) } }
    LaunchedEffect(followSongs) {
        followSongs.collect { id ->
            if (nav.currentBackStackEntry?.destination == null) return@collect
            pendingFollow.arrive(id, waitNow)?.let { nav.navigate(stageFor(it)) { popUpTo<Stage> { inclusive = true }; launchSingleTop = true } }
            sync()
        }
    }
    // what they were doing is done (or it changed what it is): what waited opens now, or the reason is told again
    LaunchedEffect(wait) {
        pendingFollow.release(wait, followingSong.value)?.let { nav.navigate(stageFor(it)) { launchSingleTop = true } }
        sync()
    }
    // the leader went elsewhere: what waits becomes where the band is now (nothing, if it isn't on anything here any more)
    LaunchedEffect(followingSong) {
        followingSong.collect { now -> if (pendingFollow.waiting != null && pendingFollow.waiting != now) { pendingFollow.moved(now); sync() } }
    }
    SideEffect {
        gate.go = { pendingFollow.take()?.let { nav.navigate(stageFor(it)) { launchSingleTop = true } }; sync() }
    }
}

/**
 * A link to a Settings section: Home, which opens Settings on it. From the stage Home goes over it (back returns to the
 * stage that was there, with its break, its page and its counters); from anywhere else it takes the place of what is
 * above Home, as before.
 */
internal fun NavController.openSettingsFrom() {
    if (currentBackStackEntry?.destination?.hasRoute<Stage>() == true) navigate(Home) else popBackStack<Home>(inclusive = false)
}
