package com.juliano.stagebook.ui.stage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.juliano.stagebook.data.model.Instrument
import com.juliano.stagebook.domain.Voicings
import com.juliano.stagebook.ui.components.ChordDiagram
import com.juliano.stagebook.ui.editor.usedChords

/** Largura e altura de cada desenho da lista de acordes (o palco reserva o espaço com elas). */
val STRIP_ITEM_WIDTH: Dp = 76.dp
val STRIP_ITEM_HEIGHT: Dp = 94.dp   // nome (≈20) + desenho (56 × 1,15) + folga

/** Os acordes da música, na ordem em que aparecem (a lista de acordes do palco). */
fun stripChords(text: String): List<String> = usedChords(text, max = 24)

/**
 * Os desenhos dos acordes da música (topo ou fim do palco), na forma guardada na música ({define}) ou na mais fácil.
 * Lê o texto da música: escolher outra variação no palco atualiza a lista na hora.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChordStrip(text: String, instrument: Instrument, color: Color, modifier: Modifier = Modifier) {
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
            ChordDiagram(c, instrument, shape, keys, color, width = if (instrument == Instrument.TECLADO) 46.dp else 56.dp, compact = true)
        }
    }
}
