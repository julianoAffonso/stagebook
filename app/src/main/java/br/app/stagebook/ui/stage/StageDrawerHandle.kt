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
import androidx.compose.foundation.layout.PaddingValues
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
    right: Boolean = false,   // on the right edge, for left-handed players: it is pulled to the left
    waiting: Int = LocalWaitingRequests.current,   // requests waiting for an answer: the number shows on the handle
) {
    val density = LocalDensity.current
    val flingPx = with(density) { Sizes.swipe.toPx() }
    val hitH = Sizes.touch * 2
    var pulled by remember { mutableFloatStateOf(0f) }
    // while held, the handle follows the finger up and down; let go and the place is kept
    var moving by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(y) }
    // the place as it is now: the gesture detector below is made once, and a second drag must start from where the first ended
    val yNow by androidx.compose.runtime.rememberUpdatedState(y)
    val onMoveNow by androidx.compose.runtime.rememberUpdatedState(onMove)
    val onOpenNow by androidx.compose.runtime.rememberUpdatedState(onOpen)
    val label = stringResource(R.string.drawer_open)
    val hint = stringResource(R.string.drawer_handle_move)
    BoxWithConstraints(modifier) {
        val heightPx = with(density) { maxHeight.toPx() }
        val hitPx = with(density) { hitH.toPx() }
        val boxW = with(density) { Spacing.xl.toPx() }
        val left = if (right) (constraints.maxWidth - boxW).coerceAtLeast(0f) else 0f
        val shown = if (moving) dragY else y
        val top = ((shown * heightPx) - hitPx / 2f).coerceIn(0f, (heightPx - hitPx).coerceAtLeast(0f))
        Box(
            Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .systemGestureExclusion()
                .size(width = Spacing.xl, height = hitH)
                .testTag(DRAWER_HANDLE_TAG)
                // a hold picks it up to move it along the edge; a short touch opens, a pull to the right opens too
                .pointerInput(heightPx) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { moving = true; dragY = yNow },
                        onDrag = { change, amount -> change.consume(); dragY = (dragY + amount.y / heightPx).coerceIn(0f, 1f) },
                        onDragEnd = { moving = false; onMoveNow(dragY) },
                        onDragCancel = { moving = false },
                    )
                }
                .draggable(
                    rememberDraggableState { pulled += it },
                    Orientation.Horizontal,
                    onDragStarted = { pulled = 0f },
                    onDragStopped = { velocity ->
                        val toward = if (right) -1f else 1f   // the way that opens it: away from its edge
                        if (!moving && (velocity * toward > flingPx || pulled * toward > flingPx)) onOpenNow()
                        pulled = 0f
                    },
                )
                .clickable(interactionSource = null, indication = null, role = Role.Button, onClickLabel = hint, onClick = onOpen)
                .semantics { contentDescription = label },
            contentAlignment = if (right) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Column(
                Modifier
                    .size(width = HANDLE_WIDTH, height = HANDLE_HEIGHT)
                    .clip(if (right) RoundedCornerShape(topStart = HANDLE_CORNER, bottomStart = HANDLE_CORNER) else RoundedCornerShape(topEnd = HANDLE_CORNER, bottomEnd = HANDLE_CORNER))
                    .overLyrics(bg, fg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                repeat(3) { Box(Modifier.size(width = Spacing.s, height = HANDLE_LINE).background(fg.copy(alpha = if (moving) 1f else HANDLE_LINE_ALPHA), CircleShape)) }
            }
            // someone asked to join (the band, the computer): open the menu to answer
            if (waiting > 0) Box(
                Modifier.align(if (right) Alignment.TopEnd else Alignment.TopStart).padding(if (right) PaddingValues(end = HANDLE_WIDTH - Spacing.s) else PaddingValues(start = HANDLE_WIDTH - Spacing.s)).size(Spacing.m + Spacing.xs).background(MaterialTheme.colorScheme.error, CircleShape),
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
