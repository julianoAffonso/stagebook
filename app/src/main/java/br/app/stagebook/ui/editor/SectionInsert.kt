package br.app.stagebook.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import br.app.stagebook.domain.Sections

/** Atalhos do editor, na ordem em que aparecem (o "Fim" é o padrão do another app do usuário). */
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
 * Põe "[Refrão]", "[Verso 2]"… numa linha só dele onde está o cursor, com uma linha em branco antes
 * quando vem depois de letra, e deixa o cursor na linha de baixo (pronto para digitar ou colar).
 * Cursor no meio de uma linha: o título entra depois dela (não corta a frase).
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
        // linha vazia: o título ocupa ela (mantendo o respiro se a de cima tem letra)
        text.substring(lineStart, lineEnd).isBlank() -> {
            val insert = (if (prevHasLyrics) "\n" else "") + header + (if (atEnd) "\n" else "")
            val out = text.replaceRange(lineStart, lineEnd, insert)
            TextFieldValue(out, TextRange(lineStart + insert.length + (if (atEnd) 0 else 1)))
        }
        // começo de uma linha com letra: o título entra antes dela
        at == lineStart -> {
            val insert = (if (prevHasLyrics) "\n" else "") + header + "\n"
            TextFieldValue(text.replaceRange(lineStart, lineStart, insert), TextRange(lineStart + insert.length))
        }
        // meio ou fim da linha: entra depois dela
        else -> {
            val insert = "\n\n" + header + (if (atEnd) "\n" else "")
            TextFieldValue(text.replaceRange(lineEnd, lineEnd, insert), TextRange(lineEnd + insert.length + (if (atEnd) 0 else 1)))
        }
    }
}

/** " 2" para o 2º verso até ali; os outros tipos não levam número. */
private fun verseNumber(text: String, before: Int, kind: Sections.Kind): String {
    if (kind != Sections.Kind.VERSE) return ""
    val verses = Sections.parse(text.substring(0, before)).count { it.header?.kind == Sections.Kind.VERSE }
    return " " + (verses + 1)
}
