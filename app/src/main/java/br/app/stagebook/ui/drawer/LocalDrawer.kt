package br.app.stagebook.ui.drawer

import androidx.compose.runtime.staticCompositionLocalOf

/** What a screen can ask of the library drawer: open it (the stage's first button). Null where there is no drawer. */
class DrawerControl(val open: () -> Unit, val close: () -> Unit, val pinned: Boolean = false)

val LocalDrawer = staticCompositionLocalOf<DrawerControl?> { null }
