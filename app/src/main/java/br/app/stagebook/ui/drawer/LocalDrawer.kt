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
