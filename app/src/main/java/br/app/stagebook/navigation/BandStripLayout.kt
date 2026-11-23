package br.app.stagebook.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity

/**
 * The screens with the "Lead the band" strip below them. The keyboard lifts all of it, strip included: with the strip
 * outside the lift it stayed behind the keyboard and a tap on "Go" was a key (device test v0.1.1134). The lift is on
 * the whole column, so the screens' own imePadding finds the keyboard already counted and pads nothing more: the
 * strip rises above the keyboard and the editor gives it exactly its height, no more.
 */
@Composable
internal fun BandStripLayout(screens: @Composable BoxScope.() -> Unit, strip: @Composable () -> Unit, liftIme: Boolean = true) {
    // [liftIme] off while the library drawer is open: the keyboard of its search must not shrink the stage behind it (the
    // stage worked its zoom out again on every show of the keyboard); the drawer lifts its own sheet
    // the screens end where the strip starts, so that much of the keyboard is already behind them: told to them as insets
    // taken. Without it the drawer's sheet, which lifts itself, left a gap of the strip's height above the keyboard
    // (device test 23/11, searching the songs while leading the band)
    var stripHeight by remember { mutableIntStateOf(0) }
    val under = with(LocalDensity.current) { stripHeight.toDp() }
    Column(if (liftIme) Modifier.imePadding() else Modifier) {
        Box(Modifier.weight(1f).consumeWindowInsets(PaddingValues(bottom = under)), content = screens)
        Box(Modifier.onSizeChanged { stripHeight = it.height }) { strip() }
    }
}
