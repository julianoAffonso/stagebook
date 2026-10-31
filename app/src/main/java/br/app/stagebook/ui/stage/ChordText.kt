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
 * Stage text with chords: each chord (marked with [CHORD_TAG]) is drawn in the space above the marked letter, which
 * [joinLines] reserved. Used on stage and in the settings preview. If a chord would still touch the one before it on
 * the same line (font wider than [chordLine] guessed), it moves right just enough.
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
    val sizes = remember(text, chordStyle) { chordsAt.map { measurer.measure(it.item, chordStyle) } }
    var laid by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text,
        style = style,
        modifier = modifier.drawWithContent {
            drawContent()
            val l = laid ?: return@drawWithContent
            var lastLine = -1
            var lastEnd = 0f
            val gap = if (chordStyle.fontSize.isSp) chordStyle.fontSize.toPx() * 0.3f else 0f
            chordsAt.forEachIndexed { i, a ->
                if (a.start >= l.layoutInput.text.length) return@forEachIndexed
                val line = l.getLineForOffset(a.start)
                var x = l.getHorizontalPosition(a.start, true)
                if (line == lastLine) x = maxOf(x, lastEnd + gap)
                drawText(sizes[i], topLeft = Offset(x, l.getLineTop(line)))
                lastLine = line
                lastEnd = x + sizes[i].size.width
            }
        },
        onTextLayout = { laid = it; onTextLayout(it) },
    )
}
