package com.juliano.stagebook.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.juliano.stagebook.domain.Chords

/** Põe `[acorde]` no lugar do cursor (ou da seleção), com o cursor logo depois, pronto para a sílaba. */
fun insertChord(value: TextFieldValue, chord: String): TextFieldValue {
    val mark = "[" + chord.trim() + "]"
    val start = minOf(value.selection.start, value.selection.end)
    val end = maxOf(value.selection.start, value.selection.end)
    val text = value.text.substring(0, start) + mark + value.text.substring(end)
    return TextFieldValue(text, TextRange(start + mark.length))
}

private val bracket = Regex("""\[([^\[\]]*)]""")

/** Os acordes da música, na ordem em que aparecem, sem repetir (atalhos do editor). */
fun usedChords(text: String, max: Int = 16): List<String> =
    bracket.findAll(text).map { it.groupValues[1] }.filter(Chords::isChord).distinct().take(max).toList()
