package com.juliano.stagebook.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juliano.stagebook.data.model.Instrument
import com.juliano.stagebook.domain.Voicings
import com.juliano.stagebook.ui.theme.PianoBlack
import com.juliano.stagebook.ui.theme.PianoWhite

/**
 * Como montar o acorde: o braço (cordas na vertical, casas na horizontal, bolinhas onde apertar, "o" corda solta,
 * "x" não toca, número da casa quando não começa na primeira) ou o teclado com as notas acesas.
 * [shape] para cordas, [keys] (notas MIDI) para o teclado. Sem margem própria: o [modifier] posiciona.
 */
@Composable
fun ChordDiagram(
    chord: String,
    instrument: Instrument,
    shape: Voicings.Shape?,
    keys: List<Int>?,
    color: Color,
    modifier: Modifier = Modifier,
    width: Dp = 160.dp,
    accent: Color = MaterialTheme.colorScheme.primary,
    compact: Boolean = false,   // lista de acordes do palco: menor, com a mesma largura para todos
) {
    Column(
        if (compact) modifier.width(STRIP_WIDTH) else modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
    ) {
        Text(chord, style = (if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge).copy(fontWeight = FontWeight.Bold), color = color, maxLines = 1)
        if (instrument == Instrument.TECLADO) Keyboard(keys.orEmpty(), accent, width) else Fretboard(shape, Voicings.strings(instrument), color, width)
    }
}

@Composable
private fun Fretboard(shape: Voicings.Shape?, strings: Int, color: Color, width: Dp) {
    val measurer = rememberTextMeasurer()
    val label = TextStyle(color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Canvas(Modifier.size(width, width * 1.15f)) {
        if (shape == null) return@Canvas
        val frets = 5
        val left = size.width * 0.18f
        val right = size.width * 0.94f
        val top = size.height * 0.16f
        val bottom = size.height * 0.98f
        val gap = (right - left) / (strings - 1)
        val row = (bottom - top) / frets
        val base = shape.baseFret
        val thin = 1.5.dp.toPx()
        // casas e cordas; a pestana do braço (nut) grossa quando começa na primeira casa
        for (i in 0..frets) drawLine(color, Offset(left, top + i * row), Offset(right, top + i * row), if (i == 0 && base == 1) 5.dp.toPx() else thin)
        for (s in 0 until strings) drawLine(color, Offset(left + s * gap, top), Offset(left + s * gap, bottom), thin)
        if (base > 1) drawText(measurer, "$base", Offset(0f, top + row * 0.2f), label)
        val dot = minOf(gap, row) * 0.28f
        shape.frets.forEachIndexed { s, f ->
            val x = left + s * gap
            when {
                f < 0 -> drawText(measurer, "x", Offset(x - 4.dp.toPx(), 0f), label)
                f == 0 -> drawCircle(color, dot * 0.7f, Offset(x, top - row * 0.45f), style = Stroke(thin))
                else -> drawCircle(color, dot, Offset(x, top + (f - base + 0.5f) * row))
            }
        }
        // pestana só quando a forma precisa dela (mais de 4 notas apertadas): a barra na menor casa
        val pressed = shape.frets.withIndex().filter { it.value > 0 }
        val lo = pressed.minOfOrNull { it.value }
        val atLo = pressed.filter { it.value == lo }
        if (lo != null && Voicings.needsBarre(shape.frets.toIntArray())) {
            val y = top + (lo - base + 0.5f) * row
            drawRoundRect(
                color,
                Offset(left + atLo.first().index * gap - dot, y - dot * 0.8f),
                Size((atLo.last().index - atLo.first().index) * gap + 2 * dot, dot * 1.6f),
                CornerRadius(dot),
            )
        }
    }
}

@Composable
private fun Keyboard(keys: List<Int>, accent: Color, width: Dp) {
    Canvas(Modifier.size(width * 1.5f, width * 0.6f)) {
        // duas oitavas a partir do Dó da nota mais grave; teclas como num piano, as do acorde na cor de destaque
        val start = ((keys.minOrNull() ?: 60) / 12) * 12
        val range = start until start + 24
        val whites = range.filter { it % 12 !in BLACK }
        val w = size.width / whites.size
        val thin = 1.dp.toPx()
        whites.forEachIndexed { i, m ->
            drawRect(if (m in keys) accent else PianoWhite, Offset(i * w, 0f), Size(w, size.height))
            drawRect(PianoBlack, Offset(i * w, 0f), Size(w, size.height), style = Stroke(thin))
        }
        range.filter { it % 12 in BLACK }.forEach { m ->
            val before = whites.indexOfLast { it < m } + 1
            drawRect(if (m in keys) accent else PianoBlack, Offset(before * w - w * 0.3f, 0f), Size(w * 0.6f, size.height * 0.6f))
        }
    }
}

private val BLACK = setOf(1, 3, 6, 8, 10)
private val STRIP_WIDTH = 76.dp

@LightPreview
@androidx.compose.runtime.Composable
private fun ChordDiagramLightPreview() = PreviewSurface(dark = false) {
    ChordDiagram("C", Instrument.VIOLAO, Voicings.Shape(listOf(-1, 3, 2, 0, 1, 0)), null, MaterialTheme.colorScheme.onSurface)
}

@DarkPreview
@androidx.compose.runtime.Composable
private fun ChordDiagramDarkPreview() = PreviewSurface(dark = true) {
    ChordDiagram("F", Instrument.VIOLAO, Voicings.Shape(listOf(1, 3, 3, 2, 1, 1)), null, MaterialTheme.colorScheme.onSurface)
}
