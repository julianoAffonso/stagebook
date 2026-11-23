package br.app.stagebook.ui.drawer

import androidx.compose.runtime.staticCompositionLocalOf

/** What a screen can ask of the library drawer: open it (the stage's first button). Null where there is no drawer. */
class DrawerControl(
    val open: () -> Unit,
    val close: () -> Unit,
    val pinned: Boolean = false,
    /** Opens the drawer on a setlist (its songs and order), and on its share sheet when asked. */
    val openSetlist: (setId: String, share: Boolean) -> Unit = { _, _ -> },
    /** The drawer is open over the screen (never while pinned beside it). */
    val isOpen: () -> Boolean = { false },
)

val LocalDrawer = staticCompositionLocalOf<DrawerControl?> { null }

/**
 * The screens inside the drawer that must be asked before the drawer closes (a setlist being edited, songs being picked,
 * a filter open): [asks] asks the latest one first and returns true when one took the request (it closes the drawer
 * itself once answered). Each screen adds and removes only its own question (audit 20/11: one screen setting the single
 * question to none wiped the one under it). [holdScrim] spends, for a moment, a touch on the dimmed area alone (the
 * touch that closed a filter lifts there): it holds that touch only, never a close the app asks for.
 */
class DrawerGuard {
    private val askers = mutableListOf<() -> Boolean>()
    fun add(ask: () -> Boolean) { askers += ask }
    fun remove(ask: () -> Boolean) { askers.removeAll { it === ask } }
    val asks: (() -> Boolean)? get() = if (askers.isEmpty()) null else ({ askers.asReversed().toList().any { it() } })

    private var scrimHeldUntil = Long.MIN_VALUE
    fun holdScrim(ms: Long) { scrimHeldUntil = android.os.SystemClock.uptimeMillis() + ms }
    fun scrimHeld(): Boolean = android.os.SystemClock.uptimeMillis() < scrimHeldUntil
}

/** How many requests wait for an answer (someone to join the band, a computer to edit): the stage's handle says so. */
val LocalWaitingRequests = androidx.compose.runtime.compositionLocalOf { 0 }

/** What the pinned menu's number of waiting requests does when tapped: the window with the first one comes up. */
val LocalAttendRequest = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * What the library drawer adds at the end of each title's line (the pin, the waiting requests): the drawer has no top row
 * of its own (user, 19/11: the logo and the name took the room of the list). Nothing outside the drawer.
 */
val LocalDrawerTitleActions = staticCompositionLocalOf<@androidx.compose.runtime.Composable () -> Unit> { {} }

val LocalDrawerGuard = staticCompositionLocalOf<DrawerGuard?> { null }
