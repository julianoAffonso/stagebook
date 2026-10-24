package com.juliano.stagebook.ui.stage

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import com.juliano.stagebook.data.model.SectionLang
import com.juliano.stagebook.domain.Sections

/**
 * Linhas da letra para o palco: títulos de seção em negrito, na cor escolhida, menores e em
 * maiúsculas. Títulos escondidos somem mas deixam o respiro entre as partes; brancos repetidos viram um.
 */
fun lyricsLines(text: String, lang: SectionLang, sectionColor: Color, showSections: Boolean): List<AnnotatedString> =
    stageLines(text, lang, sectionColor, showSections).map { it.text }

/** Uma linha do palco e a linha da letra de onde ela veio (segurar a linha abre o editor nela). */
class StageLine(val text: AnnotatedString, val source: Int)

fun stageLines(text: String, lang: SectionLang, sectionColor: Color, showSections: Boolean): List<StageLine> {
    val out = ArrayList<StageLine>()
    fun blank(at: Int) { if (out.isNotEmpty() && out.last().text.isNotEmpty()) out += StageLine(AnnotatedString(""), at) }
    Sections.parse(text).forEachIndexed { i, line ->
        val h = line.header
        when {
            h != null && !showSections -> blank(i)
            h != null -> out += StageLine(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = sectionColor, fontSize = 0.72.em, letterSpacing = 0.08.em)) {
                        append(Sections.label(h, lang).uppercase())
                    }
                },
                i,
            )
            line.text.isBlank() -> blank(i)
            else -> out += StageLine(AnnotatedString(line.text.trimEnd()), i)
        }
    }
    while (out.isNotEmpty() && out.last().text.isEmpty()) out.removeAt(out.lastIndex)
    return out
}

/** Posição na letra onde começa a linha `line` (as linhas são as do `Sections.parse`, uma por '\n'). */
fun lineStart(text: String, line: Int): Int {
    var at = 0
    repeat(line) { at = text.indexOf('\n', at).let { if (it < 0) return text.length else it + 1 } }
    return at
}

/** A letra inteira num texto só (prévia dos ajustes). */
fun lyricsAnnotated(text: String, lang: SectionLang, sectionColor: Color, showSections: Boolean): AnnotatedString =
    joinLines(lyricsLines(text, lang, sectionColor, showSections))

fun joinLines(lines: List<AnnotatedString>): AnnotatedString = buildAnnotatedString {
    lines.forEachIndexed { i, l ->
        if (i > 0) append('\n')
        append(l)
    }
}
