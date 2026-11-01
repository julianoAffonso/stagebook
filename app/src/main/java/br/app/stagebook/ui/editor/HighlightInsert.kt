package br.app.stagebook.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import br.app.stagebook.ui.stage.HIGHLIGHT_MARK
import br.app.stagebook.ui.stage.highlightMarks

/**
 * "Highlight" in the editor (vocalist, 31/10): wraps the selection in `==` so the stage paints it with a marker. No
 * selection: the word at the cursor. Cursor inside a highlight: takes it off. Only within one line (the stage reads
 * the marks line by line).
 */
fun toggleHighlight(value: TextFieldValue): TextFieldValue {
    val text = value.text
    val w = HIGHLIGHT_MARK.length
    var start = value.selection.min.coerceIn(0, text.length)
    val lineStart = text.lastIndexOf('\n', start - 1) + 1
    val lineEnd = text.indexOf('\n', start).let { if (it < 0) text.length else it }
    var end = value.selection.max.coerceIn(start, lineEnd)

    val marks = highlightMarks(text.substring(lineStart, lineEnd)).map { it + lineStart }
    for (k in marks.indices step 2) {
        val open = marks[k]
        val close = marks[k + 1]
        if (start in open..close + w) {
            val out = text.removeRange(close, close + w).removeRange(open, open + w)
            return TextFieldValue(out, TextRange(open, close - w))
        }
    }
    if (start == end) {
        while (start > lineStart && !text[start - 1].isWhitespace()) start--
        while (end < lineEnd && !text[end].isWhitespace()) end++
    }
    while (start < end && text[start].isWhitespace()) start++
    while (end > start && text[end - 1].isWhitespace()) end--
    if (start == end) return value
    val out = text.substring(0, start) + HIGHLIGHT_MARK + text.substring(start, end) + HIGHLIGHT_MARK + text.substring(end)
    return TextFieldValue(out, TextRange(start + w, end + w))
}
