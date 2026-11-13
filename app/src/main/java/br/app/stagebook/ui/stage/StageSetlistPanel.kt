package br.app.stagebook.ui.stage

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import br.app.stagebook.R
import br.app.stagebook.domain.SetlistBreaks
import br.app.stagebook.domain.SetlistShow
import br.app.stagebook.ui.components.SetlistBreakRow
import br.app.stagebook.ui.components.SongKeyBadge
import br.app.stagebook.ui.components.SetlistSongRow
import br.app.stagebook.ui.theme.Sizes
import br.app.stagebook.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * The setlist over the stage (user, 13/11): a small handle at the middle of the left edge and, pulled or tapped, a
 * panel that slides in over the lyrics, which neither move nor change size. A tap on a song goes to it ([onSong]); a
 * tap outside, a drag back or the handle again only close. Closed, nothing but the handle is on screen.
 */
@Composable
internal fun StageSetlistLayer(
    open: Boolean,
    onOpen: (Boolean) -> Unit,
    setlist: StageSetlist,
    songs: List<StageSong>,
    breaks: List<Int>,
    index: Int,
    fg: Color,
    bg: Color,
    onSong: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val window = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val width = minOf(PANEL_WIDTH, window * PANEL_MAX_SHARE)
    val widthPx = with(density) { width.toPx() }
    val flingPx = with(density) { Sizes.swipe.toPx() }
    // 0 closed, 1 open; follows the finger while dragged. Only read when placing and drawing: a slide recomposes nothing
    var slide by remember { mutableFloatStateOf(if (open) 1f else 0f) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(open, dragging) {
        if (!dragging) animate(slide, if (open) 1f else 0f, animationSpec = tween(PANEL_MS)) { v, _ -> slide = v }
    }
    val showing by remember { derivedStateOf { slide > 0f } }
    val drag = Modifier.draggable(
        rememberDraggableState { delta -> slide = (slide + delta / widthPx).coerceIn(0f, 1f) },
        Orientation.Horizontal,
        onDragStarted = { dragging = true },
        onDragStopped = { velocity ->
            // a quick flick decides; a slow drag goes to the nearer side
            onOpen(if (velocity > flingPx) true else if (velocity < -flingPx) false else slide >= 0.5f)
            dragging = false
        },
    )
    Box(modifier.fillMaxSize()) {
        if (showing || open) {
            val scrim = MaterialTheme.colorScheme.scrim
            val close = stringResource(R.string.stage_setlist_close)
            // over the whole stage: the lyrics underneath take no tap or swipe while the panel is open
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag(SETLIST_SCRIM_TAG)
                    .drawBehind { drawRect(scrim.copy(alpha = SCRIM_ALPHA * slide)) }
                    .then(drag)
                    .clickable(interactionSource = null, indication = null, onClickLabel = close) { onOpen(false) },
            )
            Surface(
                Modifier
                    .offset { IntOffset(((slide - 1f) * widthPx).roundToInt(), 0) }
                    .width(width)
                    .fillMaxHeight()
                    .testTag(SETLIST_PANEL_TAG)
                    .then(drag),
                color = MaterialTheme.colorScheme.background,
            ) {
                SetlistPanelContent(setlist, songs, breaks, index, onSong)
            }
        }
        val label = stringResource(if (open) R.string.stage_setlist_close else R.string.stage_setlist_open)
        // rides on the panel's edge. The drag has to start here: the rest of the edge is Android's back gesture, so
        // only this rectangle is kept from it
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((slide * widthPx).roundToInt(), 0) }
                .systemGestureExclusion()
                .size(width = Spacing.xl, height = Sizes.touch * 2)
                .then(drag)
                .clickable(interactionSource = null, indication = null, role = Role.Button) { onOpen(!open) }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.CenterStart,
        ) {
            Column(
                Modifier
                    .size(width = HANDLE_WIDTH, height = HANDLE_HEIGHT)
                    .clip(RoundedCornerShape(topEnd = HANDLE_CORNER, bottomEnd = HANDLE_CORNER))
                    .overLyrics(bg, fg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                repeat(3) { Box(Modifier.size(width = Spacing.s, height = HANDLE_LINE).background(fg.copy(alpha = HANDLE_LINE_ALPHA), CircleShape)) }
            }
        }
    }
}

/** The setlist's name, where the show is in it, and its songs and breaks in order, with the song on stage in view. */
@Composable
private fun SetlistPanelContent(setlist: StageSetlist, songs: List<StageSong>, breaks: List<Int>, index: Int, onSong: (Int) -> Unit) {
    val rows = remember(songs.size, breaks) { SetlistBreaks.rows(songs.size, breaks) }
    // the song before it stays in sight too: what was just played is the musician's bearing
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (rows.indexOf(index) - 1).coerceAtLeast(0))
    Column(Modifier.fillMaxSize().padding(start = Spacing.m, end = Spacing.m, top = Spacing.m)) {
        Text(setlist.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            stringResource(R.string.stage_position, index + 1, songs.size),
            Modifier.padding(bottom = Spacing.m),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
        )
        LazyColumn(Modifier.fillMaxWidth().weight(1f), list, verticalArrangement = Arrangement.spacedBy(Spacing.s), contentPadding = PaddingValues(bottom = Spacing.m)) {
            itemsIndexed(rows, key = { i, _ -> i }) { _, row ->
                if (row == SetlistBreaks.BREAK) SetlistBreakRow(handle = null, onRemove = null)
                else {
                    val song = songs[row]
                    val key = setlist.keys.getOrNull(row).orEmpty()
                    SetlistSongRow(
                        number = row + 1,
                        title = if (song.missing) stringResource(R.string.set_item_missing) else song.title,
                        subtitle = song.artist,
                        state = setlist.states.getOrElse(row) { SetlistShow.State.NONE },
                        onClick = { onSong(row) },
                        titleStyle = MaterialTheme.typography.titleMedium,
                        trailing = if (key.isEmpty()) null else ({ SongKeyBadge(key) }),
                    )
                }
            }
        }
    }
}

internal const val SETLIST_PANEL_TAG = "stage_setlist"
internal const val SETLIST_SCRIM_TAG = "stage_setlist_scrim"

private val PANEL_WIDTH = 304.dp
private const val PANEL_MAX_SHARE = 0.85f   // a narrow phone still shows the stage beside it
private const val PANEL_MS = 200
private const val SCRIM_ALPHA = 0.5f
private val HANDLE_WIDTH = Spacing.m + Spacing.xs
private val HANDLE_HEIGHT = 80.dp
private val HANDLE_CORNER = 12.dp
private val HANDLE_LINE = 2.dp
private const val HANDLE_LINE_ALPHA = 0.5f   // the play button's idle tint
