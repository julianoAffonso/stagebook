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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
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
    onClearFilter: (() -> Unit)? = null,   // a filter is on: an × after the subtitle turns it off
    iconActions: Boolean = true,   // the actions are icon buttons: their drawings go out to the margin
    actions: @Composable RowScope.() -> Unit = {},
) {
    val wide = wideLists()
    Column(modifier.fillMaxWidth()) {
        // the arrow and the icons on the title's line (centred on title and subtitle, the arrow sat below the title,
        // user 03/11); the icons' drawings on the screen's margin, like the arrow's
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            // the theme's colour, not the surroundings': on a screen with no surface under it (Review setlist) the
            // arrow came out black on the dark background (user, 02/11)
            if (onBack != null) BackButton(onBack)
            Text(
                title,
                // Google's pattern: a main screen has a big title; an inner screen (with the arrow) on phones a
                // smaller one that can wrap to two lines, centred on the icons (a long setlist name was
                // cut next to its icons); on tablets always big (it fits)
                style = when {
                    wide -> MaterialTheme.typography.headlineLarge
                    onBack != null -> MaterialTheme.typography.titleMedium
                    else -> MaterialTheme.typography.headlineMedium
                },
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                // the tap takes only the name itself, not the empty room up to the icons (that opened rename too; audit
                // 03/11): the text wraps its content inside the room it is given
                modifier = Modifier.weight(1f).wrapContentWidth(Alignment.Start)
                    .then(if (onTitleClick != null) Modifier.clickable(onClickLabel = titleClickLabel, onClick = onTitleClick) else Modifier),
            )
            Row(if (iconActions) Modifier.offset(x = HeaderIconInset) else Modifier, verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        if (subtitle.isNotEmpty()) {
            // aligned with the title (after the back button); on phones full width below, on tablets under the title
            Row(Modifier.padding(start = if (onBack != null) BackButtonWidth + Spacing.s else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                Subtitle(subtitle)
                if (onClearFilter != null) ClearFilterButton(onClearFilter)
            }
        }
    }
}

// as tall as the subtitle's line, so the list doesn't move when a filter turns on; the touch area goes past it. A faded
// red: it stands out beside the grey count without looking like a delete (user, 04/11)
@Composable
private fun ClearFilterButton(onClick: () -> Unit) {
    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
        IconButton(onClick, Modifier.requiredSize(48.dp)) {
            Icon(AppIcons.Close, stringResource(R.string.filters_clear), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
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
