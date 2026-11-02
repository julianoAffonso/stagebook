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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.app.stagebook.data.model.Instrument
import br.app.stagebook.domain.Voicings
import br.app.stagebook.ui.components.ChordDiagram
import br.app.stagebook.ui.editor.usedChords

/** Width and height of each diagram in the chord list (the stage reserves the space with them). */
val STRIP_ITEM_WIDTH: Dp = 76.dp
val STRIP_ITEM_HEIGHT: Dp = 94.dp   // nome (≈20) + desenho (56 × 1,15) + folga

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
    onHide: (String) -> Unit = {},
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
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        items.forEach { (c, shape, keys) ->
            Box {
                ChordDiagram(c, instrument, shape, keys, color, width = if (instrument == Instrument.KEYBOARD) 46.dp else 56.dp, compact = true)
                // quiet ×: removes the chord from this song's list (the stage asks to confirm)
                Box(
                    Modifier.align(Alignment.TopEnd).size(28.dp).clip(CircleShape).clickable { onHide(c) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Close, stringResource(R.string.stage_chord_hide, c), Modifier.size(14.dp), tint = color.copy(alpha = 0.45f))
                }
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
}
