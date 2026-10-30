package br.app.stagebook.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import br.app.stagebook.domain.Chords

/** Puts `[chord]` at the cursor (or over the selection), with the cursor right after it, ready for the syllable. */
fun insertChord(value: TextFieldValue, chord: String): TextFieldValue {
    val mark = "[" + chord.trim() + "]"
    val start = minOf(value.selection.start, value.selection.end)
    val end = maxOf(value.selection.start, value.selection.end)
    val text = value.text.substring(0, start) + mark + value.text.substring(end)
    return TextFieldValue(text, TextRange(start + mark.length))
}

private val bracket = Regex("""\[([^\[\]]*)]""")

/** The song's chords, in the order they appear, without repeats (editor shortcuts). */
fun usedChords(text: String, max: Int = 16): List<String> =
    bracket.findAll(text).map { it.groupValues[1] }.filter(Chords::isChord).distinct().take(max).toList()
