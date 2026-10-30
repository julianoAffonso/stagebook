package br.app.stagebook.ui.stage

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer

/**
 * Texto do palco com os acordes: cada acorde (marca [CHORD_TAG]) é desenhado no espaço em cima da letra
 * marcada, que o [joinLines] reservou. Usado no palco e na prévia dos ajustes.
 */
@Composable
fun ChordText(
    text: AnnotatedString,
    style: TextStyle,
    chordStyle: TextStyle,
    modifier: Modifier = Modifier,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val measurer = rememberTextMeasurer()
    val chordsAt = remember(text) { text.getStringAnnotations(CHORD_TAG, 0, text.length) }
    var laid by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text,
        style = style,
        modifier = modifier.drawWithContent {
            drawContent()
            val l = laid ?: return@drawWithContent
            chordsAt.forEach { a ->
                if (a.start >= l.layoutInput.text.length) return@forEach
                val line = l.getLineForOffset(a.start)
                drawText(measurer, a.item, Offset(l.getHorizontalPosition(a.start, true), l.getLineTop(line)), chordStyle)
            }
        },
        onTextLayout = { laid = it; onTextLayout(it) },
    )
}
