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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer

/**
 * Stage text with chords: each chord (marked with [CHORD_TAG]) is drawn in the space above the marked letter, which
 * [joinLines] reserved. Used on stage and in the settings preview. If a chord would still touch the one before it on
 * the same line (font wider than [chordLine] guessed), it moves right just enough. A highlighted phrase
 * ([HIGHLIGHT_TAG]) gets a marker behind the lyrics letters only, not the chord space above them.
 */
@Composable
fun ChordText(
    text: AnnotatedString,
    style: TextStyle,
    chordStyle: TextStyle,
    modifier: Modifier = Modifier,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    // no cache of its own: each distinct chord is measured once below and kept (a song repeats a few chords many times;
    // the default cache kept whole layouts; audit 01/11)
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val chordsAt = remember(text) { text.getStringAnnotations(CHORD_TAG, 0, text.length) }
    val marked = remember(text) { text.getStringAnnotations(HIGHLIGHT_TAG, 0, text.length) }
    val sections = remember(text) { text.getStringAnnotations(SECTION_TAG, 0, text.length).filter { it.item.isNotEmpty() } }
    val byName = remember(text, chordStyle) { chordsAt.map { it.item }.distinct().associateWith { measurer.measure(it, chordStyle) } }
    var laid by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text,
        style = style,
        modifier = modifier.drawWithContent {
            laid?.let { l ->
                val size = if (style.fontSize.isSp) style.fontSize.toPx() else 0f
                marked.forEach { a ->
                    val color = Color(a.item.toInt())
                    val end = a.end.coerceAtMost(l.layoutInput.text.length)
                    if (a.start >= end) return@forEach
                    for (line in l.getLineForOffset(a.start)..l.getLineForOffset(end - 1)) {
                        val from = maxOf(a.start, l.getLineStart(line))
                        val to = minOf(end, l.getLineEnd(line, visibleEnd = true))
                        if (from >= to) continue
                        // by the letters' boxes: the position just past the last letter of a paragraph comes back as 0
                        val x0 = l.getBoundingBox(from).left
                        val x1 = l.getBoundingBox(to - 1).right
                        val base = l.getLineBaseline(line)
                        // the letters' height, from the baseline; without a font size, the whole line
                        val top = if (size > 0f) base - size * 0.95f else l.getLineTop(line)
                        val bottom = if (size > 0f) base + size * 0.3f else l.getLineBottom(line)
                        drawRect(color, Offset(minOf(x0, x1), top), Size(kotlin.math.abs(x1 - x0), bottom - top))
                    }
                }
            }
            // section titles: a rounded pill in the section color (the title brings its own room, [stageLines])
            laid?.let { l ->
                sections.forEach { a ->
                    val end = a.end.coerceAtMost(l.layoutInput.text.length)
                    if (a.start >= end) return@forEach
                    // one pill per drawn line: a long title that wraps got one box from the first letter's line to the
                    // last letter's x, covering part of line one and none of line two (audit 03/11)
                    for (line in l.getLineForOffset(a.start)..l.getLineForOffset(end - 1)) {
                        val from = maxOf(a.start, l.getLineStart(line))
                        val to = minOf(end, l.getLineEnd(line, visibleEnd = true))
                        if (from >= to) continue
                        val first = l.getBoundingBox(from)
                        val last = l.getBoundingBox(to - 1)
                        val h = first.bottom - first.top
                        drawRoundRect(
                            Color(a.item.toInt()), Offset(minOf(first.left, last.left), first.top), Size(kotlin.math.abs(last.right - first.left), h),
                            androidx.compose.ui.geometry.CornerRadius(h / 2f),
                        )
                    }
                }
            }
            drawContent()
            val l = laid ?: return@drawWithContent
            val gap = if (chordStyle.fontSize.isSp) chordStyle.fontSize.toPx() * 0.3f else 0f
            // positions first: each chord over its letter, moved right if it would touch the one before; then, from the
            // end of each line, moved left if it would pass the right edge, pushing the ones before it along so they
            // keep their gap ("D7(9-)" went past the edge, then touched "Am7", device test B-14)
            class Placed(val line: Int, var x: Float, val chord: androidx.compose.ui.text.TextLayoutResult)
            val placed = ArrayList<Placed>(chordsAt.size)
            chordsAt.forEach { a ->
                if (a.start >= l.layoutInput.text.length) return@forEach
                val line = l.getLineForOffset(a.start)
                var x = l.getHorizontalPosition(a.start, true)
                val before = placed.lastOrNull()
                if (before != null && before.line == line) x = maxOf(x, before.x + before.chord.size.width + gap)
                placed += Placed(line, x, byName.getValue(a.item))
            }
            for (i in placed.indices.reversed()) {
                val p = placed[i]
                val next = placed.getOrNull(i + 1)?.takeIf { it.line == p.line }
                val limit = if (next != null) next.x - gap - p.chord.size.width else (l.size.width - p.chord.size.width).toFloat()
                p.x = minOf(p.x, limit).coerceAtLeast(0f)
            }
            placed.forEach { p -> drawText(p.chord, topLeft = Offset(p.x, l.getLineTop(p.line))) }
        },
        onTextLayout = { laid = it; onTextLayout(it) },
    )
}
