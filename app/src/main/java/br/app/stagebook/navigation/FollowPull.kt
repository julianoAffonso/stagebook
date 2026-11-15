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
import androidx.navigation.toRoute
import br.app.stagebook.data.BandConduct
import br.app.stagebook.domain.FollowWait
import br.app.stagebook.domain.PendingFollow
import br.app.stagebook.domain.followBlock
import br.app.stagebook.ui.components.FollowGate
import br.app.stagebook.ui.components.FollowPending
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

// screens the leader's song can take the follower away from: nothing is being typed or chosen there. Home's Songs and
// Setlists tabs do; its Settings tab (and the screens inside it) waits (FollowHoldWhile), and so do the editors and the
// setlist's edit screen (an action under way, held the whole time it is open)
internal fun NavDestination.pullsToStage() = hasRoute<Home>() || hasRoute<Stage>()

internal fun stageFor(target: String) = if (target == BandConduct.BREAK_ONLY) Stage(leaderBreak = true) else Stage(songId = target)

/** Is this the stage for [target]: the band's song, or the leader's break alone. */
internal fun Stage.isFor(target: String?): Boolean = target != null && stageFor(target).let { it.songId == songId && it.leaderBreak == leaderBreak }

/**
 * The stage on screen is on a song other than [target]: the person went there on their own, so the band's song does not
 * take them away; the strip says it and "Go" opens it. Not on a stage (null), nothing here to protect.
 */
internal fun Stage?.elsewhereFrom(target: String?): Boolean = this != null && !isFor(target)

/**
 * Opens the follower's stage on [target], replacing the stage already open. Without the pop, a stage on top just takes
 * the new arguments (launchSingleTop keeps its entry and so its view model, which read the old song once): the screen
 * stayed on the song before while the band was on the next (device test v0.1.1139).
 */
internal fun NavController.openFollowStage(target: String) {
    // already on that stage (the leader's break began over the song on screen): nothing to open, the stage draws the break
    val here = currentBackStackEntry?.takeIf { it.destination.hasRoute<Stage>() }?.toRoute<Stage>()
    if (here?.isFor(target) == true) return
    navigate(stageFor(target)) { popUpTo<Stage> { inclusive = true }; launchSingleTop = true }
}

/**
 * The leader changed songs: the follower's stage opens on it (replacing the open stage, without stacking), unless the
 * person is in the middle of something: a screen with typing or choosing (editor, importer, the setlist's edit screen), a
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
    val stage = entry?.takeIf { it.destination.hasRoute<Stage>() }?.toRoute<Stage>()
    val stageNow by rememberUpdatedState(stage)
    // the song the band was on before the message that arrives: a stage on it moves along, a stage elsewhere stays
    val bandWas = remember { arrayOf(followingSong.value) }
    // what waits was met by a stage already on another song (decided when the first message came, not when it opens)
    val away = remember { booleanArrayOf(false) }
    val wait: FollowWait? = here?.let {
        // on the stage only our own menus and windows hold the song: the window losing focus there (split screen, the
        // shade) must not stop a follower for good
        followBlock(it.pullsToStage(), it.hasRoute<Home>() && followHold.value, it.hasRoute<Stage>(), gate.menus > 0 || (!focused && !it.hasRoute<Stage>()), keyboard)
    }
    val waitNow by rememberUpdatedState(wait)
    fun sync() { gate.pending = pendingFollow.waiting?.let { FollowPending(it, pendingFollow.reason ?: FollowWait.SCREEN) } }
    LaunchedEffect(followSongs) {
        followSongs.collect { id ->
            if (nav.currentBackStackEntry?.destination == null) return@collect
            val before = bandWas[0]; bandWas[0] = id
            // the person is on another song of their own: not pulled, the strip tells it (the stage isn't locked to the band)
            if (pendingFollow.waiting == null) away[0] = stageNow.elsewhereFrom(before)
            val block = waitNow ?: FollowWait.OTHER_SONG.takeIf { away[0] }
            pendingFollow.arrive(id, block)?.let(nav::openFollowStage)
            sync()
        }
    }
    // what they were doing is done (or it changed what it is): what waited opens now, or the reason is told again
    LaunchedEffect(wait) {
        pendingFollow.release(wait, followingSong.value)?.let { target ->
            // the menu or window closed, but the stage is on another song: still waiting for "Go"
            if (away[0] && stageNow.elsewhereFrom(target)) pendingFollow.arrive(target, FollowWait.OTHER_SONG) else nav.openFollowStage(target)
        }
        sync()
    }
    // the person got to the band's song by themselves: nothing waits any more
    LaunchedEffect(stage) {
        if (pendingFollow.waiting != null && stage?.isFor(followingSong.value) == true) { pendingFollow.clear(); sync() }
    }
    // the leader went elsewhere: what waits becomes where the band is now (nothing, if it isn't on anything here any more)
    LaunchedEffect(followingSong) {
        followingSong.collect { now -> if (pendingFollow.waiting != null && pendingFollow.waiting != now) { pendingFollow.moved(now); sync() } }
    }
    SideEffect {
        gate.go = { pendingFollow.take()?.let(nav::openFollowStage); sync() }
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
