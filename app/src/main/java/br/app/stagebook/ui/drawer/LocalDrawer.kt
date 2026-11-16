package br.app.stagebook.ui.drawer

import androidx.compose.runtime.staticCompositionLocalOf

/** What a screen can ask of the library drawer: open it (the stage's first button). Null where there is no drawer. */
class DrawerControl(
    val open: () -> Unit,
    val close: () -> Unit,
    val pinned: Boolean = false,
    /** Opens the drawer on a setlist (its songs and order), and on its share sheet when asked. */
    val openSetlist: (setId: String, share: Boolean) -> Unit = { _, _ -> },
)

val LocalDrawer = staticCompositionLocalOf<DrawerControl?> { null }

/**
 * A screen inside the drawer that must be asked before the drawer closes (a setlist being edited): [asks] returns true
 * when it took the request and will close the drawer itself once answered.
 */
class DrawerGuard { var asks: (() -> Boolean)? = null }

/** How many requests wait for an answer (someone to join the band, a computer to edit): the stage's handle says so. */
val LocalWaitingRequests = staticCompositionLocalOf { 0 }

val LocalDrawerGuard = staticCompositionLocalOf<DrawerGuard?> { null }
