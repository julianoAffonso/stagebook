package br.app.stagebook.ui.stage

import androidx.compose.ui.res.pluralStringResource
import br.app.stagebook.domain.Chords
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onLongClick
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.app.stagebook.data.model.Instrument
import br.app.stagebook.domain.Voicings
import br.app.stagebook.ui.components.ChordDiagram
import br.app.stagebook.ui.editor.usedChords

/** Width and height of each diagram in the chord list (the stage reserves the space with them). */
val STRIP_ITEM_WIDTH: Dp = 118.dp
val STRIP_ITEM_HEIGHT: Dp = 140.dp   // nome (≈24) + desenho (90 × 1,15) + folga

/** The drawings 60% bigger than the small ones (user, 02/11: too small), drawn at that size so the lines stay thin. */
private const val STRIP_DRAW = 1.6f

/** Room kept at the end of the chord list's first row for its ×. */
val STRIP_CLOSE_WIDTH: Dp = 36.dp

/** The song's chords, in the order they appear, minus the ones the user hid from the list (all of them, no limit). */
fun stripChords(text: String): List<String> {
    val hidden = Chords.hiddenChords(text)
    return usedChords(text, max = Int.MAX_VALUE).filterNot { it in hidden }
}

/**
 * The song's chord diagrams (top or end of the stage), in the shape saved in the song ({define}) or the easiest one.
 * Reads the song text: picking another shape on stage updates the list right away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChordStrip(
    text: String,
    instrument: Instrument,
    color: Color,
    modifier: Modifier = Modifier,
    scale: Float = 1f,   // the stage widens each cell so the list fills the width (user, 02/11: it took ~3/4 of the TCL)
    onHide: (String) -> Unit = {},
    onHideAll: () -> Unit = {},
    onRestore: () -> Unit = {},
) {
    val hiddenCount = remember(text) { Chords.hiddenChords(text).size }
    val items = remember(text, instrument) {
        stripChords(text).map { c ->
            Triple(
                c,
                Voicings.savedShape(text, instrument, c) ?: Voicings.shapes(c, instrument, max = 1).firstOrNull(),
                Voicings.savedKeys(text, c) ?: Voicings.keys(c).firstOrNull(),
            )
        }
    }
    Box(modifier.fillMaxWidth()) {
    FlowRow(Modifier.padding(end = STRIP_CLOSE_WIDTH), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        items.forEach { (c, shape, keys) ->
            // holding a chord takes it off this song's list (the stage asks to confirm); one × for the whole list below,
            // instead of one on every chord (user, 02/11)
            Box(
                // the cell widens to fill the row; the drawing stays at its own size (scaled up, it looked blown up on
                // the TCL; user, 02/11)
                Modifier.size(STRIP_ITEM_WIDTH * scale, STRIP_ITEM_HEIGHT).longPressOnly(stringResource(R.string.stage_chord_hide, c)) { onHide(c) },
                contentAlignment = Alignment.TopCenter,
            ) {
                ChordDiagram(c, instrument, shape, keys, color, width = (if (instrument == Instrument.KEYBOARD) 46.dp else 56.dp) * STRIP_DRAW, compact = true)
            }
        }
        // hidden chords: one tap brings all back (no confirmation, it removes nothing from the screen)
        if (hiddenCount > 0) {
            Box(Modifier.size(STRIP_ITEM_WIDTH, 40.dp).clip(CircleShape).clickable(onClick = onRestore), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Undo, pluralStringResource(R.plurals.stage_chords_restore, hiddenCount, hiddenCount), Modifier.size(18.dp), tint = color.copy(alpha = 0.6f))
                    Text("$hiddenCount", style = MaterialTheme.typography.labelLarge, color = color.copy(alpha = 0.6f))
                }
            }
        }
    }
    // quiet × in the corner: hides the whole list in this song (the ↶ above brings it back)
    if (items.isNotEmpty()) {
        Box(Modifier.align(Alignment.TopEnd).size(STRIP_CLOSE_WIDTH).clip(CircleShape).clickable(onClick = onHideAll), contentAlignment = Alignment.Center) {
            Icon(AppIcons.Close, stringResource(R.string.stage_chords_hide_all), Modifier.size(18.dp), tint = color.copy(alpha = 0.45f))
        }
    }
    }
}

/**
 * A long press only: a tap isn't taken, so it still turns the stage's page (a clickable here swallowed page turns over
 * the whole chord list; audit 02/11). The label reaches accessibility as the long-press action.
 */
private fun Modifier.longPressOnly(label: String, onLong: () -> Unit): Modifier = this
    .semantics { onLongClick(label) { onLong(); true } }
    .pointerInput(onLong) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            // null only when the time ran out with the finger still down; a lift or a swipe (the page taking the
            // drag) ends it without the long press
            val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() ?: Unit }
            if (ended == null) {
                onLong()
                // the rest of this touch belongs to the long press
                do { val e = awaitPointerEvent(); e.changes.forEach { it.consume() } } while (e.changes.any { it.pressed })
            }
        }
    }
