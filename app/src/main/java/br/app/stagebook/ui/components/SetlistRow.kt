package br.app.stagebook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.app.stagebook.R
import br.app.stagebook.domain.SetlistShow
import br.app.stagebook.ui.theme.Sizes
import br.app.stagebook.ui.theme.Spacing

/**
 * A song of a setlist, on the setlist's screen and on the panel over the stage: where the show is on a strip at the
 * start edge (played, now, next), a small number and the title, which gets all the room left. [beforeNumber] is the
 * edit's drag handle or a selection's checkbox; without [stripRoom] it starts at the row's very edge.
 */
@Composable
fun SetlistSongRow(
    number: Int,
    title: String,
    subtitle: String,
    state: SetlistShow.State,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    inline: Boolean = false,
    titleStyle: TextStyle = MaterialTheme.typography.titleLarge,
    stripRoom: Boolean = true,
    dense: Boolean = false,
    beforeNumber: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Box(modifier.clip(MaterialTheme.shapes.medium).alpha(if (state == SetlistShow.State.PLAYED) PLAYED_ALPHA else 1f)) {
        SongListItem(
            title = title,
            subtitle = subtitle,
            onClick = onClick,
            onLongClick = onLongClick,
            titleStyle = titleStyle,
            containerColor = stateContainer(state),
            inline = inline,
            // the strip is drawn over the row's start edge; with no state its room stays empty, so the titles line up
            startPadding = if (stripRoom) STATE_STRIP + Spacing.xs else 0.dp,
            gap = Spacing.s,
            dense = dense,
            // one line: the row's end has the key and the tempo, and a long title made some rows taller than others
            // (seen on a phone with large text, 13/11)
            titleLines = 1,
            leading = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    beforeNumber?.invoke()
                    NumberBadge(
                        number,
                        color = if (state == SetlistShow.State.NOW) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                        compact = true,
                    )
                }
            },
            trailing = trailing,
        )
        if (state != SetlistShow.State.NONE) StateStrip(state, Modifier.matchParentSize().wrapContentWidth(Alignment.Start))
    }
}

/** The row's color by where the show is: the song on stage and the one after it stand out. */
@Composable
private fun stateContainer(state: SetlistShow.State): Color = when (state) {
    SetlistShow.State.NOW -> MaterialTheme.colorScheme.primaryContainer
    // the theme has no tertiary container of its own: a tint of its tertiary over the row's surface
    SetlistShow.State.NEXT -> MaterialTheme.colorScheme.tertiary.copy(alpha = NEXT_TINT).compositeOver(MaterialTheme.colorScheme.surface)
    else -> MaterialTheme.colorScheme.surface
}

/** The strip on the row's start edge, with the state written from bottom to top. */
@Composable
private fun StateStrip(state: SetlistShow.State, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (label, color, onColor) = when (state) {
        SetlistShow.State.NOW -> Triple(R.string.set_state_now, scheme.primary, scheme.onPrimary)
        SetlistShow.State.NEXT -> Triple(R.string.set_state_next, scheme.tertiary, scheme.onTertiary)
        else -> Triple(R.string.set_state_played, scheme.surfaceVariant, scheme.onSurfaceVariant)
    }
    Box(modifier.width(STATE_STRIP).fillMaxHeight().background(color), contentAlignment = Alignment.Center) {
        // smaller rather than cut: a tablet's row is barely as tall as the longest word, and less with big system text
        Text(
            stringResource(label), Modifier.vertical().padding(horizontal = Spacing.xs), style = MaterialTheme.typography.labelSmall, color = onColor, maxLines = 1, softWrap = false,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = STATE_LABEL_MIN, maxFontSize = MaterialTheme.typography.labelSmall.fontSize),
        )
    }
}

/** Turned a quarter, reading upwards: takes the room of the turned text, not of the text lying down. */
private fun Modifier.vertical(): Modifier = layout { measurable, constraints ->
    val text = measurable.measure(Constraints(maxWidth = constraints.maxHeight, maxHeight = constraints.maxWidth))
    layout(text.height, text.width) { text.place(-(text.width - text.height) / 2, -(text.height - text.width) / 2) }
}.rotate(-90f)

/**
 * The handle a row is dragged by while editing a setlist ([drag] comes from the list): a narrow column at the row's
 * very edge, as tall as a touch target, so the title keeps the width.
 */
@Composable
fun SetlistDragHandle(drag: Modifier, modifier: Modifier = Modifier) {
    // its own element for a screen reader, as a button would be, not one more word of the row
    Box(modifier.then(drag).size(width = DRAG_HANDLE, height = Sizes.touch).semantics(mergeDescendants = true) {}, contentAlignment = Alignment.Center) {
        Icon(AppIcons.DragHandle, stringResource(R.string.set_reorder), Modifier.size(DRAG_ICON), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A break between two songs: a dashed outline. While editing, dragged by its [handle] like a song (in line with the
 * songs' handles) and taken out by [onRemove].
 */
@Composable
fun SetlistBreakRow(handle: Modifier?, onRemove: (() -> Unit)?, modifier: Modifier = Modifier, dense: Boolean = false) {
    val color = MaterialTheme.colorScheme.primary
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val dash = Spacing.s.toPx()
                val line = BREAK_OUTLINE.toPx()
                // inside the row's bounds, so the line isn't cut in half at the edges
                inset(line / 2) {
                    drawOutline(shape.createOutline(size, layoutDirection, this), color, style = Stroke(line, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))))
                }
            }
            .heightIn(min = if (dense) Sizes.breakDense else Sizes.touch)
            .padding(start = if (handle != null) 0.dp else Spacing.m, end = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (handle != null) SetlistDragHandle(handle)
        // with the handle, the word starts where the songs' titles do
        Text(
            stringResource(R.string.set_break),
            Modifier.weight(1f).padding(start = if (handle != null) NUMBER_COMPACT + Spacing.s else 0.dp),
            style = MaterialTheme.typography.labelLarge, color = color,
        )
        if (onRemove != null) IconButton(onClick = onRemove) {
            Icon(AppIcons.RemoveCircle, stringResource(R.string.set_break_remove), tint = MaterialTheme.colorScheme.error)
        }
    }
}

private val STATE_STRIP = Spacing.l
private val STATE_LABEL_MIN = 8.sp
private const val PLAYED_ALPHA = 0.5f
private const val NEXT_TINT = 0.16f
private val BREAK_OUTLINE = 2.dp
private val DRAG_HANDLE = Spacing.xl
private val DRAG_ICON = 20.dp

@Composable
private fun SetlistRowsSample() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SetlistSongRow(1, "Malandragem", "Cássia Eller", SetlistShow.State.PLAYED, {}, trailing = { SongFacts("G", 96, 2, false) })
        SetlistSongRow(2, "Hazard", "Richard Marx", SetlistShow.State.NOW, {}, trailing = { SongFacts("Em", 120, 0, true) })
        SetlistBreakRow(null, null)
        SetlistSongRow(3, "Tempo Perdido", "Legião Urbana", SetlistShow.State.NEXT, {})
        SetlistSongRow(4, "Wild Horses", "The Rolling Stones", SetlistShow.State.NONE, {}, stripRoom = false, beforeNumber = { SetlistDragHandle(Modifier) })
        SetlistBreakRow(Modifier, {})
    }
}

@LightPreview
@Composable
private fun SetlistRowsLightPreview() = PreviewSurface(dark = false) { SetlistRowsSample() }

@DarkPreview
@Composable
private fun SetlistRowsDarkPreview() = PreviewSurface(dark = true) { SetlistRowsSample() }
