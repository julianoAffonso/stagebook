package br.app.stagebook.ui.stage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
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

/** How far up and down the edge the handle may go (a share of the height), on the stage and on the Settings pages. */
internal const val HANDLE_MIN = 0.1f
internal const val HANDLE_MAX = 0.9f

/**
 * The small handle at the middle of the stage's left edge (user, 15/11: the header's ☰ goes, the side handle stays and
 * the title gets the room). Tapped, or pulled to the right, it opens the library drawer: on the setlist the stage came
 * from, or on the lists. Only this rectangle takes the drag: the rest of the edge is Android's back gesture. Held, it
 * follows the finger and stays where the person lets go: up or down the edge (user, 15/11), and on the other edge when
 * let go past the middle (user, 19/11: in place of a switch in Settings). [onMove] gets the height and the side.
 */
@Composable
internal fun StageDrawerHandle(
    onOpen: () -> Unit, fg: Color, bg: Color, y: Float, onMove: (y: Float, right: Boolean) -> Unit, modifier: Modifier = Modifier,
    right: Boolean = false,   // on the right edge (left-handed players): it is pulled to the left, and the menu opens there
    waiting: Int = LocalWaitingRequests.current,   // requests waiting for an answer: the number shows on the handle
) {
    val density = LocalDensity.current
    val flingPx = with(density) { Sizes.swipe.toPx() }
    val hitH = Sizes.touch * 2
    // while held, the handle follows the finger up and down; let go and the place is kept
    var moving by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(y) }
    var dragX by remember { mutableFloatStateOf(0f) }   // where the finger is across the screen, while held
    // where the handle is placed now: the finger's positions come relative to the handle, which moves under it (and jumps
    // to the other edge), so each one is turned into a place on the screen before measuring the move
    val placed = remember { floatArrayOf(0f, 0f) }
    // the place as it is now: the gesture detector below is made once, and a second drag must start from where the first ended
    val yNow by androidx.compose.runtime.rememberUpdatedState(y)
    val rightNow by androidx.compose.runtime.rememberUpdatedState(right)
    val onMoveNow by androidx.compose.runtime.rememberUpdatedState(onMove)
    val onOpenNow by androidx.compose.runtime.rememberUpdatedState(onOpen)
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val label = stringResource(R.string.drawer_open)
    val hint = stringResource(R.string.drawer_handle_move)
    BoxWithConstraints(modifier) {
        val heightPx = with(density) { maxHeight.toPx() }
        val hitPx = with(density) { hitH.toPx() }
        val boxW = with(density) { Spacing.xl.toPx() }
        val widthPx = constraints.maxWidth.toFloat()
        // held, it shows on the side the finger is on: let go there and it stays. Only the side is read while composing
        // (it changes the shape); the place follows the finger in layout, with no composition at each move (audit 20/11)
        val side by remember(widthPx) { androidx.compose.runtime.derivedStateOf { if (moving) dragX > widthPx / 2f else rightNow } }
        Box(
            Modifier
                .offset {
                    val left = if (side) (widthPx - boxW).coerceAtLeast(0f) else 0f
                    val shown = if (moving) dragY else yNow
                    val top = ((shown * heightPx) - hitPx / 2f).coerceIn(0f, (heightPx - hitPx).coerceAtLeast(0f))
                    IntOffset(left.roundToInt(), top.roundToInt())
                }
                .onPlaced { val p = it.positionInParent(); placed[0] = p.x; placed[1] = p.y }
                .systemGestureExclusion()
                .size(width = Spacing.xl, height = hitH)
                .testTag(DRAWER_HANDLE_TAG)
                // one reader for the whole gesture (user, 19/11: the handle seemed stuck, then obeyed): let go at once is a
                // tap and opens; a move up or down takes the handle along at once, with no hold first; a move sideways
                // before the long press is a pull and opens when it goes away from the edge; held still, the long press
                // picks the handle up with a buzz, and it follows the finger, to the other edge too, until let go
                .pointerInput(heightPx, widthPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val slop = viewConfiguration.touchSlop
                        val velocity = androidx.compose.ui.input.pointer.util.VelocityTracker()
                        velocity.addPosition(down.uptimeMillis, down.position)
                        var travel = androidx.compose.ui.geometry.Offset.Zero
                        // null: the long press came with the finger still; true: let go first; false: moved first
                        val early: Boolean? = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            var result: Boolean? = null
                            while (result == null) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                velocity.addPosition(change.uptimeMillis, change.position)
                                if (!change.pressed) { change.consume(); result = true; break }
                                travel += change.positionChange()
                                change.consume()
                                if (kotlin.math.abs(travel.x) > slop || kotlin.math.abs(travel.y) > slop) result = false
                            }
                            result ?: true
                        }
                        val upOrDown = early == false && kotlin.math.abs(travel.y) > kotlin.math.abs(travel.x)
                        if (early == null) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        when {
                            early == true -> onOpenNow()
                            early == false && !upOrDown -> {
                                // a pull: followed to its end, it opens when it went away from the edge, far or fast enough
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    velocity.addPosition(change.uptimeMillis, change.position)
                                    if (change.pressed) travel += change.positionChange()
                                    change.consume()
                                    if (!change.pressed) break
                                }
                                val toward = if (rightNow) -1f else 1f   // the way that opens it: away from its edge
                                if (travel.x * toward > flingPx || velocity.calculateVelocity().x * toward > flingPx) onOpenNow()
                            }
                            else -> {
                                moving = true
                                dragY = yNow
                                fun onScreen(at: androidx.compose.ui.geometry.Offset) = androidx.compose.ui.geometry.Offset(placed[0] + at.x, placed[1] + at.y)
                                var last = onScreen(down.position + travel)
                                dragX = last.x
                                try {
                                    while (true) {
                                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) { change.consume(); onMoveNow(dragY, dragX > widthPx / 2f); break }
                                        val now = onScreen(change.position)
                                        dragY = (dragY + (now.y - last.y) / heightPx).coerceIn(0f, 1f)
                                        dragX = now.x.coerceIn(0f, widthPx)
                                        last = now
                                        change.consume()
                                    }
                                } finally {
                                    moving = false
                                }
                            }
                        }
                    }
                }
                .semantics { role = Role.Button; onClick(label = hint) { onOpenNow(); true } }
                .semantics { contentDescription = label },
            contentAlignment = if (side) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Column(
                Modifier
                    .size(width = HANDLE_WIDTH, height = HANDLE_HEIGHT)
                    .clip(if (side) RoundedCornerShape(topStart = HANDLE_CORNER, bottomStart = HANDLE_CORNER) else RoundedCornerShape(topEnd = HANDLE_CORNER, bottomEnd = HANDLE_CORNER))
                    .overLyrics(bg, fg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                repeat(3) { Box(Modifier.size(width = Spacing.s, height = HANDLE_LINE).background(fg.copy(alpha = if (moving) 1f else HANDLE_LINE_ALPHA), CircleShape)) }
            }
            // someone asked to join (the band, the computer): open the menu to answer
            if (waiting > 0) Box(
                Modifier.align(if (side) Alignment.TopEnd else Alignment.TopStart).padding(if (side) PaddingValues(end = HANDLE_WIDTH - Spacing.s) else PaddingValues(start = HANDLE_WIDTH - Spacing.s)).size(Spacing.m + Spacing.xs).background(MaterialTheme.colorScheme.error, CircleShape),
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
