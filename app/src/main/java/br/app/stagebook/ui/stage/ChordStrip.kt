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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.onClick
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ripple
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.app.stagebook.data.model.Instrument
import br.app.stagebook.domain.Voicings
import br.app.stagebook.ui.components.ChordDiagram
import br.app.stagebook.ui.editor.usedChords

/** A cell of the chord list (the stage reserves the space with it) and how much bigger than the small ones its drawing is. */
data class StripCell(val width: Dp, val height: Dp, val draw: Float)

/** The drawings 60% bigger than the small ones (user, 02/11: too small), drawn at that size so the lines stay thin. */
val STRIP_CELL = StripCell(110.dp, 140.dp, 1.6f)
private val STRIP_CELL_SMALL = StripCell(86.dp, 112.dp, 1.28f)

/**
 * Fewer than 3 chords a row at the full size (a phone held upright): smaller cells fit 3, in less height (user, 04/11).
 * The same on a [page] with room for a single row of the full size (a phone on its side).
 */
fun stripCell(room: Dp, page: Dp = Dp.Infinity): StripCell =
    if (room < STRIP_CELL.width * 3 || page < STRIP_CELL.height * 2) STRIP_CELL_SMALL else STRIP_CELL

/** The chord list's ×: it sits on the corner, half over the margin, and takes no room from the chords. */
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
    nameColor: Color,
    modifier: Modifier = Modifier,
    cell: StripCell = STRIP_CELL,
    scale: Float = 1f,   // the stage widens each cell so the list fills the width (user, 02/11: it took ~3/4 of the TCL)
    from: Int = 0,   // the chords of this column: a long list runs on like the lyrics, at full size
    until: Int = Int.MAX_VALUE,
    onHide: (String) -> Unit = {},
    onShow: ((String) -> Unit)? = null,   // a tap on a chord opens its diagram (null: the tap turns the page)
    onHideAll: () -> Unit = {},
    onRestore: () -> Unit = {},
) {
    val hiddenCount = remember(text) { Chords.hiddenChords(text).size }
    val capo = remember(text, instrument) { Voicings.neckCapo(text, instrument) }
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        items.subList(minOf(from, items.size), minOf(until, items.size)).forEach { (c, shape, keys) ->
            val pressed = remember { MutableInteractionSource() }   // the tapped chord's ripple
            // holding a chord takes it off this song's list (the stage asks to confirm); one × for the whole list below,
            // instead of one on every chord (user, 02/11). A tap opens the same diagram as a chord in the lyrics.
            Box(
                // the cell widens to fill the row; the drawing stays at its own size (scaled up, it looked blown up on
                // the TCL; user, 02/11)
                Modifier.size(cell.width * scale, cell.height)
                    .chordTouch(c, stringResource(R.string.stage_chord_hide, c), pressed, onShow, onHide),
                contentAlignment = Alignment.TopCenter,
            ) {
                ChordDiagram(c, instrument, shape, keys, color, width = (if (instrument == Instrument.KEYBOARD) 46.dp else 56.dp) * cell.draw, compact = true, capo = capo, nameColor = nameColor)
            }
        }
        // hidden chords: one tap brings all back (no confirmation, it removes nothing from the screen)
        if (hiddenCount > 0 && until >= items.size) {
            Box(Modifier.size(cell.width, 40.dp).clip(CircleShape).clickable(onClick = onRestore), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Undo, pluralStringResource(R.plurals.stage_chords_restore, hiddenCount, hiddenCount), Modifier.size(18.dp), tint = color.copy(alpha = 0.6f))
                    Text("$hiddenCount", style = MaterialTheme.typography.labelLarge, color = color.copy(alpha = 0.6f))
                }
            }
        }
    }
    // quiet × in the corner: hides the whole list in this song (the ↶ above brings it back)
    if (items.isNotEmpty() && from == 0) {
        Box(Modifier.align(Alignment.TopEnd).offset(x = STRIP_CLOSE_WIDTH / 2).size(STRIP_CLOSE_WIDTH).clip(CircleShape).clickable(onClick = onHideAll), contentAlignment = Alignment.Center) {
            Icon(AppIcons.Close, stringResource(R.string.stage_chords_hide_all), Modifier.size(18.dp), tint = color.copy(alpha = 0.45f))
        }
    }
    }
}

/**
 * A tap ([onTap], when given) and a long press, and nothing else: a drag or a second finger is left alone, so swipes and
 * pinches started on the list still reach the stage (a clickable here swallowed page turns over the whole chord list;
 * audit 02/11). Without [onTap] a tap isn't taken either, and turns the page. The tap is consumed only on the lift, which
 * the stage reads as "a button on top used it". The label reaches accessibility as the long-press action.
 */
private fun Modifier.chordTouch(
    chord: String,
    label: String,
    interaction: MutableInteractionSource,
    onTap: ((String) -> Unit)?,
    onLong: (String) -> Unit,
): Modifier {
    // the ripple only where a tap does something
    val pressed = interaction.takeIf { onTap != null }
    return this
        .then(if (pressed != null) Modifier.indication(pressed, ripple()) else Modifier)
        .semantics {
            onLongClick(label) { onLong(chord); true }
            if (onTap != null) onClick { onTap(chord); true }
        }
        .pointerInput(chord, pressed, onTap, onLong) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val press = pressed?.let { PressInteraction.Press(down.position).also(it::tryEmit) }
                // true: lifted in place (a tap); false: dragged, pinched or taken by someone else; null: the time ran
                // out with the finger still down (a long press)
                val tapped: Boolean? = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    var result: Boolean? = null
                    while (result == null) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        result = when {
                            change == null || change.isConsumed || event.changes.count { it.pressed } > 1 ||
                                (change.position - down.position).getDistance() > viewConfiguration.touchSlop -> false
                            !change.pressed -> { if (onTap != null) change.consume(); true }
                            else -> null
                        }
                    }
                    result
                }
                press?.let { pressed.tryEmit(if (tapped == true) PressInteraction.Release(it) else PressInteraction.Cancel(it)) }
                when (tapped) {
                    true -> onTap?.invoke(chord)
                    false -> Unit
                    null -> {
                        onLong(chord)
                        // the rest of this touch belongs to the long press
                        do { val e = awaitPointerEvent(); e.changes.forEach { it.consume() } } while (e.changes.any { it.pressed })
                    }
                }
            }
        }
}
