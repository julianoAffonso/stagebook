package br.app.stagebook.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import br.app.stagebook.domain.HIGHLIGHT_MARK
import br.app.stagebook.domain.highlightMarks

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
    // nothing to highlight, or only leftover marks ("=="): unchanged, the editor says how
    if (start == end || text.substring(start, end).all { it == '=' }) return value
    val out = text.substring(0, start) + HIGHLIGHT_MARK + text.substring(start, end) + HIGHLIGHT_MARK + text.substring(end)
    // the cursor after the closing mark, nothing selected: the phrase stayed selected and the next key replaced it
    // (device test B-28)
    return TextFieldValue(out, TextRange(end + 2 * w))
}

/**
 * Shows the highlights in the editor (user, 01/11, B-28): the phrase between `==` gets the stage's marker background
 * and the marks themselves fade, so what is highlighted is visible without going to the stage. The text and the cursor
 * positions don't change (only the look).
 */
class HighlightLook(private val marker: androidx.compose.ui.graphics.Color, private val marks: androidx.compose.ui.graphics.Color) :
    androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        if (!text.text.contains(HIGHLIGHT_MARK)) return androidx.compose.ui.text.input.TransformedText(text, androidx.compose.ui.text.input.OffsetMapping.Identity)
        val out = androidx.compose.ui.text.AnnotatedString.Builder(text)
        val w = HIGHLIGHT_MARK.length
        var lineStart = 0
        for (line in text.text.split('\n')) {
            val at = highlightMarks(line)
            for (k in at.indices step 2) {
                val open = lineStart + at[k]
                val close = lineStart + at[k + 1]
                out.addStyle(androidx.compose.ui.text.SpanStyle(background = marker), open + w, close)
                out.addStyle(androidx.compose.ui.text.SpanStyle(color = marks), open, open + w)
                out.addStyle(androidx.compose.ui.text.SpanStyle(color = marks), close, close + w)
            }
            lineStart += line.length + 1
        }
        return androidx.compose.ui.text.input.TransformedText(out.toAnnotatedString(), androidx.compose.ui.text.input.OffsetMapping.Identity)
    }
}
