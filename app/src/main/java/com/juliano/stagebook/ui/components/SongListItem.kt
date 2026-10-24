package com.juliano.stagebook.ui.components

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
import androidx.compose.ui.unit.dp
import com.juliano.stagebook.ui.theme.Sizes
import com.juliano.stagebook.ui.theme.Spacing

/**
 * Linha de lista (música, setlist, item do setlist): cartão arredondado com ripple ao tocar.
 * `inline`: título e subtítulo na mesma linha (tablet: mais músicas na tela sem diminuir a letra).
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
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = if (inline) Sizes.touch else Sizes.row)
                .padding(horizontal = Spacing.m, vertical = if (inline) Spacing.xs else Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            leading?.invoke()
            if (inline) {
                // o título tem prioridade; o que não couber do artista/ano vira "…"
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

/** Tablet (ou celular deitado): listas em linha única. */
@Composable
fun wideLists(): Boolean = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } >= 600.dp

/** Número da música no setlist, na cor de destaque e sem fundo (largura fixa: os títulos ficam alinhados). */
@Composable
fun NumberBadge(number: Int, modifier: Modifier = Modifier) {
    Box(modifier.width(40.dp), contentAlignment = Alignment.Center) {
        Text(number.toString(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
}

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
