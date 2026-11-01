package br.app.stagebook.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.app.stagebook.R
import br.app.stagebook.ui.theme.Sizes
import br.app.stagebook.ui.theme.Spacing

/**
 * Edges the screens respect: the app hides the system bars, so only the camera on the sides (phone in landscape); on
 * top the content goes up to the edge (on the S26 the camera gap in portrait wasted space).
 */
val ScreenInsets: WindowInsets
    @Composable get() = WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)

/**
 * Screen header: back (optional), big title, subtitle and actions on the right. On phones the subtitle runs on its own
 * line below the header, full width (between the title and the icons it got squeezed onto several lines); on tablets it
 * sits under the title.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    onBack: (() -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,   // tapping the title (e.g. rename a setlist, user 01/11); its label for accessibility
    titleClickLabel: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val wide = wideLists()
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(AppIcons.Back, contentDescription = stringResource(R.string.action_back)) }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    // Google's pattern: a main screen has a big title; an inner screen (with the arrow) on phones a
                    // smaller one that can wrap to two lines, centred on the icons (user, 01/11: "Show de sába…" was
                    // cut next to the setlist's icons); on tablets always big (it fits)
                    style = when {
                        wide -> MaterialTheme.typography.headlineLarge
                        onBack != null -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.headlineMedium
                    },
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (onTitleClick != null) Modifier.clickable(onClickLabel = titleClickLabel, onClick = onTitleClick) else Modifier,
                )
                if (wide && subtitle.isNotEmpty()) Subtitle(subtitle)
            }
            actions()
        }
        if (!wide && subtitle.isNotEmpty()) {
            // aligned with the title (after the back button)
            Subtitle(subtitle, Modifier.padding(start = if (onBack != null) Sizes.touch + Spacing.s else 0.dp))
        }
    }
}

@Composable
private fun Subtitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** Centered content with a max width: on a tablet in landscape the lines aren't stretched too far. */
@Composable
fun CenteredContent(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = Sizes.contentMax).fillMaxWidth(), content = content)
    }
}

@LightPreview
@Composable
private fun ScreenHeaderLightPreview() = PreviewSurface(dark = false) {
    ScreenHeader("Músicas", subtitle = "42 músicas")
}

@DarkPreview
@Composable
private fun ScreenHeaderDarkPreview() = PreviewSurface(dark = true) {
    ScreenHeader("Show de sábado", subtitle = "12 músicas", onBack = {}) {
        IconButton(onClick = {}) { Icon(AppIcons.Edit, contentDescription = null) }
    }
}
