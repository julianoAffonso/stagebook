package br.app.stagebook.ui.stage

import br.app.stagebook.domain.HIGHLIGHT_MARK
import br.app.stagebook.domain.WORD_JOIN
import br.app.stagebook.domain.highlightMarks

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.toArgb
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

// a section title line: pagination keeps it with the verse after it (device test B-13)
const val SECTION_TAG = "section"
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
        Sections.Kind.RIFF to stringResource(R.string.section_riff),
        Sections.Kind.PRELUDE to stringResource(R.string.section_prelude),
        Sections.Kind.PRE_VERSE to stringResource(R.string.section_pre_verse),
        Sections.Kind.LINK to stringResource(R.string.section_link),
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
    sectionFont: androidx.compose.ui.text.font.FontFamily? = null,
    highlight: Color = Color(br.app.stagebook.ui.theme.StagePalette.HIGHLIGHT),
): List<AnnotatedString> = stageLines(text, names, sectionColor, showSections, chords, sectionScale, highlight, sectionFont).map { it.text }

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
    highlight: Color = Color(br.app.stagebook.ui.theme.StagePalette.HIGHLIGHT),
    sectionFont: androidx.compose.ui.text.font.FontFamily? = null,   // null: the lyrics font
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
                        fontWeight = FontWeight.Bold, color = sectionColor, fontSize = sectionScale.em, letterSpacing = 0.08.em, fontFamily = sectionFont,
                        background = sectionColor.copy(alpha = SECTION_BACKGROUND_ALPHA),
                    ),
                ) {
                    pushStringAnnotation(SECTION_TAG, "")
                    append(label.uppercase())
                    pop()
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
            !Chords.hasChords(raw) -> out += StageLine(highlighted(AnnotatedString(raw.trimEnd()), highlight), i)
            !chords.show -> Chords.stripChords(raw).takeIf { it.isNotBlank() }?.let { out += StageLine(highlighted(AnnotatedString(it), highlight), i) }
            else -> out += StageLine(highlighted(chordLine(raw, chords), highlight), i)
        }
    }
    while (out.isNotEmpty() && out.last().text.isEmpty()) out.removeAt(out.lastIndex)
    return out
}

internal fun tabLine(raw: String, em: Float = TAB_EM): AnnotatedString = buildAnnotatedString {
    withAnnotation(TAB_TAG, "") {
        // monospace on purpose: every dash and fret is the same width, so the strings stay aligned. (The box-drawing
        // dash looked continuous but came from another font, which pushed the strings with more frets out of line, 31/10)
        withStyle(SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = em.em)) { append(raw) }
    }
}

private val bracket = Regex("""\[([^\[\]]*)]""")

/**
 * Line with chords. Chords only (intro, solo): the chords themselves, in their color. With lyrics: the lyrics, with
 * space above each drawn line (taller line height) and a [CHORD_TAG] mark on the letter under each chord; the stage
 * draws the chord there. A stretch too short for the chord is padded with spaces, or the next chord would cover it.
 * The room is measured by width, not letters: "Csus4" in bold is wider than five letters of lyrics (user, 31/10: in A
 * Little Respect the chords landed on top of each other).
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
    // chords only, maybe with marks like "x4" or "|" (user, 03/11: "[A] [B] 2x" put the 2x under the chords, as lyrics):
    // the chords in their color and the marks between them, in order, on one line
    if (lyric.isBlank() || lyric.trim().split(SPACES).all(Chords::isChordLineMark)) {
        return buildAnnotatedString {
            segs.forEach { s ->
                if (s.chord != null) {
                    if (length > 0) append("   ")
                    withStyle(SpanStyle(color = look.color, fontWeight = FontWeight.Bold, fontSize = look.scale.em)) { append(s.chord) }
                }
                s.text.trim().split(SPACES).filter { it.isNotEmpty() }.forEach { if (length > 0) append("   "); append(it) }
            }
        }
    }
    segs.forEachIndexed { i, s ->
        val chord = s.chord ?: return@forEachIndexed
        // the last chord needs its own width too (a chord at the end of the line needs a letter to sit on)
        val need = emWidth(chord) * look.scale * BOLD + if (i < segs.lastIndex) CHORD_GAP else 0f
        val letters = s.text.length
        while (emWidth(s.text) < need) s.text += " "
        // room opened in the middle of a word ("gu[A9]erra") gets a hyphen, as printed chord sheets do: the bare gap
        // read as two words (user, 03/11)
        val next = segs.getOrNull(i + 1)?.text?.firstOrNull()
        if (s.text.length > letters && s.text.getOrNull(letters - 1)?.isLetter() == true && next?.isLetter() == true) {
            val mid = letters + (s.text.length - letters) / 2
            s.text = s.text.substring(0, mid) + WORD_JOIN + s.text.substring(mid + 1)
        }
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

/**
 * Highlighted stretch of a stage line (item = ARGB color). [ChordText] paints it behind the lyrics only: a background
 * style would also cover the chord space above a line with chords.
 */
const val HIGHLIGHT_TAG = "highlight"


/**
 * Takes the `==` marks out of a stage line and marks what was between them with [HIGHLIGHT_TAG] (a marker background). The line's
 * styles and marks move with the text; a chord that sat on a mark moves to the letter after it.
 */
internal fun highlighted(line: AnnotatedString, color: Color): AnnotatedString {
    val text = line.text
    val marks = highlightMarks(text)
    if (marks.isEmpty()) return line
    val w = HIGHLIGHT_MARK.length
    fun at(x: Int): Int = x - marks.sumOf { m -> (x - m).coerceIn(0, w) }
    val b = AnnotatedString.Builder()
    var from = 0
    marks.forEach { m -> b.append(text.substring(from, m)); from = m + w }
    b.append(text.substring(from))
    val length = b.length
    line.spanStyles.forEach { b.addStyle(it.item, at(it.start), at(it.end)) }
    line.paragraphStyles.forEach { b.addStyle(it.item, at(it.start), at(it.end)) }
    line.getStringAnnotations(0, text.length).forEach { a ->
        val start = at(a.start)
        val end = at(a.end).let { if (it <= start) start + 1 else it }
        if (start < length) b.addStringAnnotation(a.tag, a.item, start, end.coerceAtMost(length))
    }
    for (k in marks.indices step 2) b.addStringAnnotation(HIGHLIGHT_TAG, color.toArgb().toString(), at(marks[k]), at(marks[k + 1]))
    return b.toAnnotatedString()
}

// rough width of text in em, by kind of letter (proportional font): enough to keep one chord off the next
private val SPACES = Regex("""\s+""")
private const val BOLD = 1.1f
private const val CHORD_GAP = 0.5f

internal fun emWidth(text: String): Float = text.sumOf { c ->
    when {
        c == '=' -> 0.0   // highlight marks: they leave the line ([highlighted])
        c == ' ' || c in "iljI.,;:'!|()" -> 0.28
        c in "mwMW" -> 0.85
        c.isUpperCase() || c == '#' -> 0.68
        c.isDigit() -> 0.56
        else -> 0.52
    }
}.toFloat()

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
    sectionFont: androidx.compose.ui.text.font.FontFamily? = null,
    highlight: Color = Color(br.app.stagebook.ui.theme.StagePalette.HIGHLIGHT),
): AnnotatedString = joinLines(lyricsLines(text, names, sectionColor, showSections, chords, sectionScale, sectionFont, highlight))

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
