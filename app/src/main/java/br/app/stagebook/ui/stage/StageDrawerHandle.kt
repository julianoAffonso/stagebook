package br.app.stagebook.ui.stage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import br.app.stagebook.R
import br.app.stagebook.ui.theme.Sizes
import br.app.stagebook.ui.theme.Spacing

/**
 * The small handle at the middle of the stage's left edge (user, 15/11: the header's ☰ goes, the side handle stays and
 * the title gets the room). Tapped, or pulled to the right, it opens the library drawer: on the setlist the stage came
 * from, or on the lists. Only this rectangle takes the drag: the rest of the edge is Android's back gesture.
 */
@Composable
internal fun StageDrawerHandle(onOpen: () -> Unit, fg: Color, bg: Color, modifier: Modifier = Modifier) {
    val flingPx = with(LocalDensity.current) { Sizes.swipe.toPx() }
    var pulled by remember { mutableFloatStateOf(0f) }
    val label = stringResource(R.string.drawer_open)
    Box(
        modifier
            .systemGestureExclusion()
            .size(width = Spacing.xl, height = Sizes.touch * 2)
            .testTag(DRAWER_HANDLE_TAG)
            .draggable(
                rememberDraggableState { pulled += it },
                Orientation.Horizontal,
                onDragStarted = { pulled = 0f },
                onDragStopped = { velocity -> if (velocity > flingPx || pulled > flingPx) onOpen(); pulled = 0f },
            )
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onOpen)
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

internal const val DRAWER_HANDLE_TAG = "stage_drawer_handle"
private val HANDLE_WIDTH = Spacing.m + Spacing.xs
private val HANDLE_HEIGHT = 80.dp
private val HANDLE_CORNER = 12.dp
private val HANDLE_LINE = 2.dp
private const val HANDLE_LINE_ALPHA = 0.5f   // the play button's idle tint
