package br.app.stagebook.ui.components

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import br.app.stagebook.R

/**
 * What the main tabs say about the app without taking a strip of the screen (user, 01/11: the essential mode and band
 * strips were too much): essential mode on and how many things from the band are waiting.
 */
data class HomeStatus(
    val essential: Boolean, val received: Int, val onEssential: () -> Unit, val onReceived: () -> Unit,
    val webAddress: String = "", val onWebEditor: () -> Unit = {},   // the web editor while it's on (user, 01/11)
)

/** Given by the Home screen to the tabs' headers; none elsewhere. */
val LocalHomeStatus = staticCompositionLocalOf<HomeStatus?> { null }

/** The icons next to a main tab's title: the leaf (essential mode) and the band with the count; nothing when all quiet. */
@Composable
fun HomeStatusIcons() {
    val status = LocalHomeStatus.current ?: return
    if (status.webAddress.isNotEmpty()) {
        IconButton(onClick = status.onWebEditor) {
            Icon(AppIcons.Computer, stringResource(R.string.web_in_use), tint = MaterialTheme.colorScheme.primary)
        }
    }
    if (status.essential) {
        IconButton(onClick = status.onEssential) {
            Icon(AppIcons.Eco, stringResource(R.string.essential_banner), tint = MaterialTheme.colorScheme.primary)
        }
    }
    if (status.received > 0) {
        IconButton(onClick = status.onReceived) {
            BadgedBox(badge = { Badge { Text(status.received.toString()) } }) {
                Icon(AppIcons.Group, pluralStringResource(R.plurals.status_received, status.received, status.received))
            }
        }
    }
}

@LightPreview
@Composable
private fun HomeStatusIconsLightPreview() = PreviewSurface(dark = false) {
    CompositionLocalProvider(LocalHomeStatus provides HomeStatus(true, 2, {}, {}, "http://192.168.0.10:8090")) { ScreenHeader("Músicas", subtitle = "42 músicas") { HomeStatusIcons() } }
}

@DarkPreview
@Composable
private fun HomeStatusIconsDarkPreview() = PreviewSurface(dark = true) {
    CompositionLocalProvider(LocalHomeStatus provides HomeStatus(true, 2, {}, {})) { ScreenHeader("Músicas", subtitle = "42 músicas") { HomeStatusIcons() } }
}
