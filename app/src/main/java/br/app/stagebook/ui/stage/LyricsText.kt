package br.app.stagebook.ui.stage

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withAnnotation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import br.app.stagebook.R
import br.app.stagebook.domain.Chords
import br.app.stagebook.data.model.Look
import br.app.stagebook.domain.Sections
import br.app.stagebook.domain.Tablature

/** Marca, no texto da linha, a letra embaixo da qual um acorde é desenhado (o valor é o acorde). */
const val CHORD_TAG = "chord"

/** Marca a linha de letra com acordes; o valor é a altura da linha (em em) com o espaço dos acordes. */
private const val CHORD_LINE_TAG = "chordline"

/** Marca o espaço da lista de acordes da música (topo ou fim); o palco desenha os acordes por cima dele. */
const val CHORD_LIST_TAG = "chordlist"

/** Linha de tablatura (T22): letra de máquina, nunca quebra; o palco diminui até caber na coluna ([fitTab]). */
const val TAB_TAG = "tab"
const val TAB_EM = 0.7f

/** Uma linha vazia de [heightEm] de altura, reservada para a lista de acordes (entra na paginação como as outras). */
fun chordListLine(heightEm: Float): AnnotatedString = buildAnnotatedString {
    withAnnotation(CHORD_LINE_TAG, heightEm.toString()) { withAnnotation(CHORD_LIST_TAG, "") { append(' ') } }
}

/** Altura da linha da letra, em relação à fonte (a mesma do estilo do palco). */
private const val LYRIC_LINE = 1.25f

/** Como mostrar acordes: [show] false = perfil "só letra"; [scale] = tamanho do acorde em relação à letra. */
class ChordLook(val show: Boolean, val scale: Float, val color: Color)

/**
 * Nomes das seções reconhecidas na língua do app ([translate]), ou como estão escritas na música.
 * A letra guardada nunca muda: só o que aparece (decisão do usuário, 24/10).
 */
class SectionNames(private val names: Map<Sections.Kind, String>?) {
    fun label(h: Sections.Header): String = names?.get(h.kind)?.let { Sections.label(h, it) } ?: h.original
}

@Composable
fun rememberSectionNames(translate: Boolean): SectionNames {
    if (!translate) return SectionNames(null)
    val names = mapOf(
        Sections.Kind.INTRO to stringResource(R.string.section_intro),
        Sections.Kind.VERSE to stringResource(R.string.section_verse),
        Sections.Kind.PRE_CHORUS to stringResource(R.string.section_pre_chorus),
        Sections.Kind.CHORUS to stringResource(R.string.section_chorus),
        Sections.Kind.BRIDGE to stringResource(R.string.section_bridge),
        Sections.Kind.SOLO to stringResource(R.string.section_solo),
        Sections.Kind.INTERLUDE to stringResource(R.string.section_interlude),
        Sections.Kind.OUTRO to stringResource(R.string.section_outro),
    )
    return SectionNames(names)
}

/**
 * Linhas da letra para o palco: títulos de seção em negrito, na cor escolhida, menores e em
 * maiúsculas. Títulos escondidos somem mas deixam o respiro entre as partes; brancos repetidos viram um.
 */
fun lyricsLines(
    text: String, names: SectionNames, sectionColor: Color, showSections: Boolean, chords: ChordLook,
    sectionScale: Float = Look.DEFAULT_SECTION_SCALE,
): List<AnnotatedString> = stageLines(text, names, sectionColor, showSections, chords, sectionScale).map { it.text }

/** Uma linha do palco e a linha da letra de onde ela veio (segurar a linha abre o editor nela). */
class StageLine(val text: AnnotatedString, val source: Int)

// diretivas do ChordPro que abrem uma seção
private val sectionDirectives = mapOf(
    "start_of_chorus" to Sections.Kind.CHORUS, "soc" to Sections.Kind.CHORUS,
    "start_of_verse" to Sections.Kind.VERSE, "sov" to Sections.Kind.VERSE,
    "start_of_bridge" to Sections.Kind.BRIDGE, "sob" to Sections.Kind.BRIDGE,
)
private val directiveName = Regex("""^\s*\{\s*([A-Za-z_][A-Za-z_-]*)\s*(?::\s*(.*?))?\s*}\s*$""")

fun stageLines(
    text: String, names: SectionNames, sectionColor: Color, showSections: Boolean, chords: ChordLook,
    sectionScale: Float = Look.DEFAULT_SECTION_SCALE,
): List<StageLine> {
    val out = ArrayList<StageLine>()
    fun blank(at: Int) { if (out.isNotEmpty() && out.last().text.isNotEmpty()) out += StageLine(AnnotatedString(""), at) }
    fun header(label: String, at: Int) {
        if (!showSections) return blank(at)
        out += StageLine(
            buildAnnotatedString {
                // mais destaque (pedido do usuário, 29/10): tamanho ajustável e um fundo leve na cor da seção
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold, color = sectionColor, fontSize = sectionScale.em, letterSpacing = 0.08.em,
                        background = sectionColor.copy(alpha = SECTION_BACKGROUND_ALPHA),
                    ),
                ) {
                    append(label.uppercase())
                }
            },
            at,
        )
    }
    val tabs = Tablature.tabLines(text)
    text.split('\n').forEachIndexed { i, raw ->
        // tablatura: só no perfil letra e cifra, em letra de máquina (as colunas ficam alinhadas)
        if (tabs[i]) {
            if (chords.show) out += StageLine(tabLine(raw.trimEnd()), i)
            return@forEachIndexed
        }
        val d = directiveName.matchEntire(raw)
        if (d != null) {
            val name = d.groupValues[1].lowercase()
            val value = d.groupValues[2]
            sectionDirectives[name]?.let { kind ->
                header(names.label(Sections.Header(kind, "", "", value.ifEmpty { kind.en })), i)
            }
            // comentário do ChordPro ({c: Solo de sax}) aparece como um título; as outras (key, capo, fim de seção…) não
            if ((name == "comment" || name == "c" || name == "ci") && value.isNotEmpty()) header(value, i)
            return@forEachIndexed
        }
        val h = Sections.detect(raw)
        when {
            h != null -> header(names.label(h), i)
            raw.isBlank() -> blank(i)
            !Chords.hasChords(raw) -> out += StageLine(AnnotatedString(raw.trimEnd()), i)
            !chords.show -> Chords.stripChords(raw).takeIf { it.isNotBlank() }?.let { out += StageLine(AnnotatedString(it), i) }
            else -> out += StageLine(chordLine(raw, chords), i)
        }
    }
    while (out.isNotEmpty() && out.last().text.isEmpty()) out.removeAt(out.lastIndex)
    return out
}

internal fun tabLine(raw: String, em: Float = TAB_EM): AnnotatedString = buildAnnotatedString {
    withAnnotation(TAB_TAG, "") {
        withStyle(SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = em.em)) { append(raw) }
    }
}

private val bracket = Regex("""\[([^\[\]]*)]""")

/**
 * Linha com acordes. Só acordes (intro, solo): os próprios acordes, na cor deles. Com letra: a letra, com um
 * espaço em cima de cada linha desenhada (altura de linha maior) e uma marca [CHORD_TAG] na letra embaixo de
 * cada acorde; o palco desenha o acorde ali. Um trecho curto demais para o acorde é completado com espaços,
 * senão o acorde seguinte o cobriria.
 */
internal fun chordLine(raw: String, look: ChordLook): AnnotatedString {
    data class Seg(val chord: String?, var text: String)
    val segs = ArrayList<Seg>()
    var last = 0
    bracket.findAll(raw).forEach { m ->
        val inner = m.groupValues[1]
        if (!Chords.isChord(inner)) return@forEach   // [Refrão] no meio da linha: fica como texto
        val before = raw.substring(last, m.range.first)
        if (segs.isEmpty()) segs += Seg(null, before) else segs.last().text += before
        segs += Seg(inner, "")
        last = m.range.last + 1
    }
    if (segs.isEmpty()) return AnnotatedString(raw.trimEnd())
    segs.last().text += raw.substring(last)
    val lyric = segs.joinToString("") { it.text }
    if (lyric.isBlank()) {
        return buildAnnotatedString {
            withStyle(SpanStyle(color = look.color, fontWeight = FontWeight.Bold, fontSize = look.scale.em)) {
                append(segs.mapNotNull { it.chord }.joinToString("   "))
            }
        }
    }
    segs.forEachIndexed { i, s ->
        val chord = s.chord ?: return@forEachIndexed
        val need = chord.length + if (i < segs.lastIndex) 1 else 0
        if (s.text.length < need) s.text = s.text.padEnd(need)
    }
    // a altura maior entra como estilo de parágrafo só ao juntar as linhas ([joinLines])
    return buildAnnotatedString {
        withAnnotation(CHORD_LINE_TAG, (LYRIC_LINE + look.scale * 1.15f).toString()) {
            segs.forEach { s ->
                if (s.chord != null) {
                    pushStringAnnotation(CHORD_TAG, s.chord)
                    append(s.text.take(1))
                    pop()
                    append(s.text.drop(1))
                } else {
                    append(s.text)
                }
            }
        }
    }
}

/** Posição na letra onde começa a linha `line` (uma por '\n'). */
fun lineStart(text: String, line: Int): Int {
    var at = 0
    repeat(line) { at = text.indexOf('\n', at).let { if (it < 0) return text.length else it + 1 } }
    return at
}

/** A letra inteira num texto só (prévia dos ajustes). */
fun lyricsAnnotated(
    text: String, names: SectionNames, sectionColor: Color, showSections: Boolean, chords: ChordLook,
    sectionScale: Float = Look.DEFAULT_SECTION_SCALE,
): AnnotatedString = joinLines(lyricsLines(text, names, sectionColor, showSections, chords, sectionScale))

private const val SECTION_BACKGROUND_ALPHA = 0.16f

/** Marca cada linha do palco com o número dela no texto montado (o "segurar a linha" acha a linha por aqui). */
const val LINE_TAG = "line"

/**
 * Junta as linhas do palco num texto só (o mesmo para medir e para desenhar). Linha com acordes vira um
 * parágrafo próprio, com a altura maior (espaço dos acordes em cima, letra embaixo). Entre parágrafos o
 * Compose já quebra a linha: uma quebra a mais ali vira uma linha vazia (medido em teste), então a quebra só
 * entra entre duas linhas comuns. Linha vazia vira um espaço (sem ele, ela sumiria entre parágrafos).
 * [starts]: se dado, recebe onde cada linha começa.
 */
fun joinLines(lines: List<AnnotatedString>, starts: IntArray? = null): AnnotatedString = buildAnnotatedString {
    lines.forEachIndexed { i, l ->
        val start = length
        starts?.set(i, start)
        if (l.isEmpty()) append(' ') else append(l)
        addStringAnnotation(LINE_TAG, i.toString(), start, length)
        val height = l.chordLineHeight()
        if (height != null) {
            addStyle(
                ParagraphStyle(lineHeight = height.em, lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Bottom, LineHeightStyle.Trim.None)),
                start,
                length,
            )
        }
        if (i < lines.lastIndex && height == null && lines[i + 1].chordLineHeight() == null) append('\n')
    }
}

/** Linha de letra com acordes (com o espaço deles em cima)? */
fun AnnotatedString.isChordLine(): Boolean = chordLineHeight() != null

private fun AnnotatedString.chordLineHeight(): Float? =
    getStringAnnotations(CHORD_LINE_TAG, 0, length).firstOrNull()?.item?.toFloat()
