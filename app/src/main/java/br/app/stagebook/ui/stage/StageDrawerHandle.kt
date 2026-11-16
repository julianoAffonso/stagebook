package br.app.stagebook.ui.stage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import br.app.stagebook.ui.drawer.LocalWaitingRequests
import br.app.stagebook.R
import br.app.stagebook.ui.theme.Sizes
import br.app.stagebook.ui.theme.Spacing

/**
 * The small handle at the middle of the stage's left edge (user, 15/11: the header's ☰ goes, the side handle stays and
 * the title gets the room). Tapped, or pulled to the right, it opens the library drawer: on the setlist the stage came
 * from, or on the lists. Only this rectangle takes the drag: the rest of the edge is Android's back gesture. Held and
 * dragged up or down, it moves along the edge and stays where the person puts it (user, 15/11).
 */
@Composable
internal fun StageDrawerHandle(
    onOpen: () -> Unit, fg: Color, bg: Color, y: Float, onMove: (Float) -> Unit, modifier: Modifier = Modifier,
    waiting: Int = LocalWaitingRequests.current,   // requests waiting for an answer: the number shows on the handle
) {
    val density = LocalDensity.current
    val flingPx = with(density) { Sizes.swipe.toPx() }
    val hitH = Sizes.touch * 2
    var pulled by remember { mutableFloatStateOf(0f) }
    // while held, the handle follows the finger up and down; let go and the place is kept
    var moving by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(y) }
    val label = stringResource(R.string.drawer_open)
    val hint = stringResource(R.string.drawer_handle_move)
    BoxWithConstraints(modifier) {
        val heightPx = with(density) { maxHeight.toPx() }
        val hitPx = with(density) { hitH.toPx() }
        val shown = if (moving) dragY else y
        val top = ((shown * heightPx) - hitPx / 2f).coerceIn(0f, (heightPx - hitPx).coerceAtLeast(0f))
        Box(
            Modifier
                .offset { IntOffset(0, top.roundToInt()) }
                .systemGestureExclusion()
                .size(width = Spacing.xl, height = hitH)
                .testTag(DRAWER_HANDLE_TAG)
                // a hold picks it up to move it along the edge; a short touch opens, a pull to the right opens too
                .pointerInput(heightPx) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { moving = true; dragY = y },
                        onDrag = { change, amount -> change.consume(); dragY = (dragY + amount.y / heightPx).coerceIn(0f, 1f) },
                        onDragEnd = { moving = false; onMove(dragY) },
                        onDragCancel = { moving = false },
                    )
                }
                .draggable(
                    rememberDraggableState { pulled += it },
                    Orientation.Horizontal,
                    onDragStarted = { pulled = 0f },
                    onDragStopped = { velocity -> if (!moving && (velocity > flingPx || pulled > flingPx)) onOpen(); pulled = 0f },
                )
                .clickable(interactionSource = null, indication = null, role = Role.Button, onClickLabel = hint, onClick = onOpen)
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
                repeat(3) { Box(Modifier.size(width = Spacing.s, height = HANDLE_LINE).background(fg.copy(alpha = if (moving) 1f else HANDLE_LINE_ALPHA), CircleShape)) }
            }
            // someone asked to join (the band, the computer): open the menu to answer
            if (waiting > 0) Box(
                Modifier.align(Alignment.TopStart).padding(start = HANDLE_WIDTH - Spacing.s).size(Spacing.m + Spacing.xs).background(MaterialTheme.colorScheme.error, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(waiting.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onError)
            }
        }
    }
}

internal const val DRAWER_HANDLE_TAG = "stage_drawer_handle"
private val HANDLE_WIDTH = Spacing.m + Spacing.xs
private val HANDLE_HEIGHT = 80.dp
private val HANDLE_CORNER = 12.dp
private val HANDLE_LINE = 2.dp
private const val HANDLE_LINE_ALPHA = 0.5f   // the play button's idle tint
