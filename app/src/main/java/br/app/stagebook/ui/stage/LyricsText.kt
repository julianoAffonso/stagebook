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

/** Marks, in the line's text, the letter a chord is drawn above (the value is the chord). */
const val CHORD_TAG = "chord"

/** Marks a lyrics line with chords; the value is the line height (in em) including the chord space. */
private const val CHORD_LINE_TAG = "chordline"

/** Marks the space for the song's chord list (top or end); the stage draws the chords over it. */
const val CHORD_LIST_TAG = "chordlist"

/** Tablature line (T22): monospace, never wraps; the stage shrinks it to fit the column ([fitTab]). */
const val TAB_TAG = "tab"
const val TAB_EM = 0.7f

/** An empty line [heightEm] tall, reserved for the chord list (paginated like the others). */
fun chordListLine(heightEm: Float): AnnotatedString = buildAnnotatedString {
    withAnnotation(CHORD_LINE_TAG, heightEm.toString()) { withAnnotation(CHORD_LIST_TAG, "") { append(' ') } }
}

/** Lyrics line height, relative to the font (the same as the stage style). */
private const val LYRIC_LINE = 1.25f

/** How to show chords: [show] false = "lyrics only" profile; [scale] = chord size relative to the lyrics. */
class ChordLook(val show: Boolean, val scale: Float, val color: Color)

/**
 * Names of recognized sections in the app's language ([translate]), or as written in the song. The saved lyrics never
 * change: only what shows (user's decision, 24/10).
 */
// a data class: equal names are the same key, so the stage doesn't redo the lyrics on every recomposition (audit 30/10)
data class SectionNames(private val names: Map<Sections.Kind, String>?) {
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
 * Lyrics lines for the stage: section titles in bold, in the chosen color, smaller and in capitals. Hidden titles go
 * away but leave the breathing room between parts; repeated blanks become one.
 */
fun lyricsLines(
    text: String, names: SectionNames, sectionColor: Color, showSections: Boolean, chords: ChordLook,
    sectionScale: Float = Look.DEFAULT_SECTION_SCALE,
): List<AnnotatedString> = stageLines(text, names, sectionColor, showSections, chords, sectionScale).map { it.text }

/** A stage line and the lyrics line it came from (holding the line opens the editor on it). */
class StageLine(val text: AnnotatedString, val source: Int)

// ChordPro directives that open a section
private val sectionDirectives = mapOf(
    "start_of_chorus" to Sections.Kind.CHORUS, "soc" to Sections.Kind.CHORUS,
    "start_of_verse" to Sections.Kind.VERSE, "sov" to Sections.Kind.VERSE,
    "start_of_bridge" to Sections.Kind.BRIDGE, "sob" to Sections.Kind.BRIDGE,
)
private val directiveName = Regex("""^\s*\{\s*([A-Za-z_][A-Za-z_-]*)\s*(?::\s*(.*?))?\s*\}\s*$""")

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
                // more emphasis (user's request, 29/10): adjustable size and a light background in the section color
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
        // tablature: only in the lyrics and chords profile, in monospace (the columns stay aligned)
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
            // a ChordPro comment ({c: Sax solo}) shows as a title; the others (key, capo, end of section…) don't
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
 * Line with chords. Chords only (intro, solo): the chords themselves, in their color. With lyrics: the lyrics, with
 * space above each drawn line (taller line height) and a [CHORD_TAG] mark on the letter under each chord; the stage
 * draws the chord there. A stretch too short for the chord is padded with spaces, or the next chord would cover it.
 */
internal fun chordLine(raw: String, look: ChordLook): AnnotatedString {
    data class Seg(val chord: String?, var text: String)
    val segs = ArrayList<Seg>()
    var last = 0
    bracket.findAll(raw).forEach { m ->
        val inner = m.groupValues[1]
        if (!Chords.isChord(inner)) return@forEach   // [Chorus] in the middle of a line: stays as text
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
    // the taller height comes in as a paragraph style only when joining the lines ([joinLines])
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

/** Position in the lyrics where line `line` starts (one per '\n'). */
fun lineStart(text: String, line: Int): Int {
    var at = 0
    repeat(line) { at = text.indexOf('\n', at).let { if (it < 0) return text.length else it + 1 } }
    return at
}

/** The whole lyrics in a single text (settings preview). */
fun lyricsAnnotated(
    text: String, names: SectionNames, sectionColor: Color, showSections: Boolean, chords: ChordLook,
    sectionScale: Float = Look.DEFAULT_SECTION_SCALE,
): AnnotatedString = joinLines(lyricsLines(text, names, sectionColor, showSections, chords, sectionScale))

private const val SECTION_BACKGROUND_ALPHA = 0.16f

/** Marks each stage line with its number in the assembled text (holding a line finds it through this). */
const val LINE_TAG = "line"

/**
 * Joins the stage lines into one text (the same for measuring and drawing). A line with chords becomes its own
 * paragraph, with the taller height (chord space on top, lyrics below). Between paragraphs Compose already breaks the
 * line: an extra break there becomes an empty line (measured in a test), so the break only goes between two plain
 * lines. An empty line becomes a space (without it, it would vanish between paragraphs). [starts]: if given, gets where
 * each line starts.
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

/** Lyrics line with chords (with their space on top)? */
fun AnnotatedString.isChordLine(): Boolean = chordLineHeight() != null

private fun AnnotatedString.chordLineHeight(): Float? =
    getStringAnnotations(CHORD_LINE_TAG, 0, length).firstOrNull()?.item?.toFloat()
