package br.app.stagebook.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The screens with the "Lead the band" strip below them. The keyboard lifts all of it, strip included: with the strip
 * outside the lift it stayed behind the keyboard and a tap on "Go" was a key (device test v0.1.1134). The lift is on
 * the whole column, so the screens' own imePadding finds the keyboard already counted and pads nothing more: the
 * strip rises above the keyboard and the editor gives it exactly its height, no more.
 */
@Composable
internal fun BandStripLayout(screens: @Composable BoxScope.() -> Unit, strip: @Composable () -> Unit) {
    Column(Modifier.imePadding()) {
        Box(Modifier.weight(1f), content = screens)
        strip()
    }
}
