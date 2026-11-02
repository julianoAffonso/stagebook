package br.app.stagebook.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import br.app.stagebook.ui.settings.SettingsSection

/**
 * Opens a Settings section from anywhere in the app (user, 01/11: where a text says how to get somewhere on this
 * device, it's a link that goes there). The navigation provides it; the default does nothing (previews, tests).
 */
val LocalOpenSettings = staticCompositionLocalOf<(SettingsSection) -> Unit> { {} }

/**
 * A text with a link in it: [template] has "%1$s" where [link] goes. The link is in the accent colour and semibold, no
 * underline, like the one in the essential mode notice (user, 30/10).
 */
@Composable
fun LinkText(
    template: String,
    link: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val accent = MaterialTheme.colorScheme.primary
    val at = template.indexOf(MARK)
    val text = buildAnnotatedString {
        append(if (at < 0) template else template.substring(0, at))
        withLink(LinkAnnotation.Clickable(TAG, TextLinkStyles(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold))) { onClick() }) {
            append(if (at < 0) " $link" else link)
        }
        if (at >= 0) append(template.substring(at + MARK.length))
    }
    Text(text, style = style, color = color, modifier = modifier)
}

/** [LinkText] whose link opens a Settings section. */
@Composable
fun SettingsLinkText(
    template: String,
    link: String,
    section: SettingsSection,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val open = LocalOpenSettings.current
    LinkText(template, link, { open(section) }, modifier, style, color)
}

private const val MARK = "%1\$s"
private const val TAG = "settings"

@LightPreview
@Composable
private fun LinkTextLightPreview() = PreviewSurface(dark = false) {
    LinkText("O que você mudar aqui fica só nesta música. Para mudar todas, use o %1\$s.", "mixer global", {})
}

@DarkPreview
@Composable
private fun LinkTextDarkPreview() = PreviewSurface(dark = true) {
    LinkText("O que você mudar aqui fica só nesta música. Para mudar todas, use o %1\$s.", "mixer global", {})
}
