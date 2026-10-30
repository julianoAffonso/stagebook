package br.app.stagebook.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import br.app.stagebook.domain.Sections

/** Editor shortcuts, in the order they appear ("Outro" is the user's another app default). */
val SECTION_SHORTCUTS = listOf(
    Sections.Kind.INTRO,
    Sections.Kind.VERSE,
    Sections.Kind.PRE_CHORUS,
    Sections.Kind.CHORUS,
    Sections.Kind.BRIDGE,
    Sections.Kind.SOLO,
    Sections.Kind.INTERLUDE,
    Sections.Kind.OUTRO,
)

/**
 * Puts "[Chorus]", "[Verse 2]"… on a line of its own at the cursor, with a blank line before it when it follows lyrics,
 * and leaves the cursor on the line below (ready to type or paste). Cursor in the middle of a line: the title goes
 * after it (doesn't cut the phrase).
 */
fun insertSection(value: TextFieldValue, kind: Sections.Kind): TextFieldValue {
    val text = value.text
    val at = value.selection.min.coerceIn(0, text.length)
    val lineStart = text.lastIndexOf('\n', at - 1) + 1
    val lineEnd = text.indexOf('\n', at).let { if (it < 0) text.length else it }
    val header = "[" + kind.pt + verseNumber(text, lineStart, kind) + "]"

    fun lineBefore(start: Int): String? =
        if (start == 0) null else text.substring(text.lastIndexOf('\n', start - 2) + 1, start - 1)
    val prevHasLyrics = lineBefore(lineStart)?.isNotBlank() == true
    val atEnd = lineEnd == text.length

    return when {
        // empty line: the title takes it (keeping the breathing room if the line above has lyrics)
        text.substring(lineStart, lineEnd).isBlank() -> {
            val insert = (if (prevHasLyrics) "\n" else "") + header + (if (atEnd) "\n" else "")
            val out = text.replaceRange(lineStart, lineEnd, insert)
            TextFieldValue(out, TextRange(lineStart + insert.length + (if (atEnd) 0 else 1)))
        }
        // start of a line with lyrics: the title goes before it
        at == lineStart -> {
            val insert = (if (prevHasLyrics) "\n" else "") + header + "\n"
            TextFieldValue(text.replaceRange(lineStart, lineStart, insert), TextRange(lineStart + insert.length))
        }
        // middle or end of the line: it goes after it
        else -> {
            val insert = "\n\n" + header + (if (atEnd) "\n" else "")
            TextFieldValue(text.replaceRange(lineEnd, lineEnd, insert), TextRange(lineEnd + insert.length + (if (atEnd) 0 else 1)))
        }
    }
}

/** " 2" for the 2nd verse so far; the other kinds don't get a number. */
private fun verseNumber(text: String, before: Int, kind: Sections.Kind): String {
    if (kind != Sections.Kind.VERSE) return ""
    val verses = Sections.parse(text.substring(0, before)).count { it.header?.kind == Sections.Kind.VERSE }
    return " " + (verses + 1)
}
