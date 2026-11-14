package br.app.stagebook.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** The id a control in Settings carries (read by the search and by the guard test), or [CHROME] for what isn't searchable. */
val SettingsItemKey = SemanticsPropertyKey<String>("SettingsItem")
var SemanticsPropertyReceiver.settingsItemId by SettingsItemKey

const val SETTINGS_CHROME = "-"

/**
 * Registers a control with the Settings index (its [id] is the one in `SettingsIndex`), and makes it the target of a
 * search result: it scrolls into view and shows a border for a moment. The index and the screen meet here: the guard
 * test fails for a control drawn without an id, and for an id the index doesn't know.
 */
@Composable
fun Modifier.settingsItem(id: String): Modifier {
    val focus = LocalSettingsFocus.current
    val mine = focus?.takeIf { it.item == id }
    val bring = remember { BringIntoViewRequester() }
    var flash by remember { mutableStateOf(false) }
    LaunchedEffect(mine?.nonce) {
        if (mine == null) return@LaunchedEffect
        bring.bringIntoView()
        flash = true
        delay(1600)
        flash = false
    }
    val width by animateFloatAsState(if (flash) 3f else 0f, label = "found item")
    val line = MaterialTheme.colorScheme.primary
    return this
        .semantics { settingsItemId = id }
        .bringIntoViewRequester(bring)
        .then(if (width > 0f) Modifier.border(BorderStroke(width.dp, line), MaterialTheme.shapes.small) else Modifier)
}

/** Marks what is on a Settings page but isn't an item to search (the ⓘ helps). */
fun Modifier.settingsChrome(): Modifier = semantics { settingsItemId = SETTINGS_CHROME }
