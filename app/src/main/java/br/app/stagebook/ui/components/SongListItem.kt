package br.app.stagebook.ui.components

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.app.stagebook.ui.theme.Sizes
import br.app.stagebook.ui.theme.Spacing

/**
 * List row (song, setlist, setlist item): rounded card with ripple on tap. `inline`: title and subtitle on the same
 * line (tablet: more songs on screen without shrinking the text).
 */
@Composable
fun SongListItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    inline: Boolean = false,
    onLongClick: (() -> Unit)? = null,   // hold: selection (share several), where the list supports it
    startPadding: Dp = Spacing.m,   // more when something is drawn over the row's start edge (a setlist's state strip)
    gap: Dp = Spacing.m,   // between the leading, the texts and the trailing; a setlist's rows keep them closer
) {
    Surface(
        modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = if (inline) Sizes.touch else Sizes.row)
                .padding(start = startPadding, end = Spacing.m)
                .padding(vertical = if (inline) Spacing.xs else Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            leading?.invoke()
            if (inline) {
                // the title has priority; whatever doesn't fit of the artist/year becomes "…"
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Text(title, style = titleStyle, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotEmpty()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
            } else Column(Modifier.weight(1f)) {
                Text(title, style = titleStyle, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

/** Tablet (or phone in landscape): single-line lists. */
@Composable
fun wideLists(): Boolean = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } >= 600.dp

/**
 * Song number in the setlist, in the accent color with no background (fixed width: the titles line up). [compact]: a
 * small number in a narrow column, which leaves the width to the title.
 */
@Composable
fun NumberBadge(number: Int, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, compact: Boolean = false) {
    Box(modifier.width(if (compact) NUMBER_COMPACT else 40.dp), contentAlignment = Alignment.Center) {
        Text(number.toString(), style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge, color = color, maxLines = 1, softWrap = false)
    }
}

/** The compact number's column. */
internal val NUMBER_COMPACT = Spacing.l + Spacing.xs

@Composable
private fun SongListItemSample(song: SampleSong) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SongListItem(title = song.title, subtitle = song.subtitle, onClick = {}, trailing = {
            Icon(AppIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        })
        SongListItem(
            title = song.title,
            subtitle = song.subtitle,
            onClick = {},
            titleStyle = MaterialTheme.typography.titleLarge,
            leading = { NumberBadge(3) },
            trailing = { Icon(AppIcons.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        )
    }
}

@LightPreview
@Composable
private fun SongListItemLightPreview(@PreviewParameter(SampleSongProvider::class) song: SampleSong) =
    PreviewSurface(dark = false) { SongListItemSample(song) }

@DarkPreview
@Composable
private fun SongListItemDarkPreview(@PreviewParameter(SampleSongProvider::class) song: SampleSong) =
    PreviewSurface(dark = true) { SongListItemSample(song) }
