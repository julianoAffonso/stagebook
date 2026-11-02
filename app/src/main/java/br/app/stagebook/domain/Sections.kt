package br.app.stagebook.domain

import br.app.stagebook.data.model.SectionLang

/**
 * Section titles (chorus, verse, bridge…) in Portuguese and English: recognizes the whole line, with or without
 * brackets/parentheses/colons, number and "2x", to show as a highlighted title on stage.
 */
object Sections {
    enum class Kind(val pt: String, val en: String, val keys: List<String>) {
        INTRO("Intro", "Intro", listOf("intro", "introducao", "introduction", "abertura")),
        VERSE("Verso", "Verse", listOf("verse", "verso", "estrofe", "parte", "stanza", "primeira parte", "segunda parte", "terceira parte")),
        PRE_CHORUS("Pré-refrão", "Pre-Chorus", listOf("pre chorus", "prechorus", "pre refrao", "prerefrao", "pre")),
        CHORUS("Refrão", "Chorus", listOf("chorus", "refrao", "refrain", "coro")),
        BRIDGE("Ponte", "Bridge", listOf("bridge", "ponte")),
        SOLO("Solo", "Solo", listOf("solo", "guitar solo", "solo de guitarra")),
        INTERLUDE("Interlúdio", "Interlude", listOf("interlude", "interludio", "instrumental", "break", "passagem")),
        OUTRO("Fim", "Outro", listOf("outro", "final", "fim", "ending", "coda", "encerramento")),   // "Fim": a common default
        // the user's library also names these parts (01/11)
        RIFF("Riff", "Riff", listOf("riff", "riff de abertura", "opening riff")),
        PRELUDE("Prelúdio", "Prelude", listOf("prelude", "preludio")),
        PRE_VERSE("Pré-estrofe", "Pre-Verse", listOf("pre estrofe", "preestrofe", "pre verse", "preverse", "pre verso")),
        LINK("Link", "Link", listOf("link")),
        // any other name written as a part ("[Solo Violino]", "[Virada bateria]"): shown as written (user, 01/11)
        OTHER("", "", emptyList()),
    }

    /** [note]: what the chart wrote next to the part, kept as written ("(repeat)", "— Base 2x"). */
    data class Header(val kind: Kind, val number: String, val repeat: String, val original: String, val note: String = "")

    /** A line of the text: either lyrics or a section title. */
    data class Line(val text: String, val header: Header?)

    private val shape = Regex("^[\\[(*#\\s]*(.+?)[\\])*\\s]*:?\\s*$")
    private val tail = Regex("^(.*?)\\s*(\\d+)?\\s*(\\(?\\s*(?:x\\s*\\d+|\\d+\\s*x|\\d+\\s*vezes)\\s*\\)?)?\\s*$")
    private val brackets = Regex("[\\])(\\[]")
    private val digits = Regex("(\\d+)")
    private val tabPrefix = Regex("^tab(?:\\s*[-:]\\s*|\\s+)(?=\\S)")

    /** Is the whole line a section title? (only keyword, number and repeat; no lyrics) */
    fun detect(line: String): Header? {
        val t = line.trim()
        if (t.isEmpty() || t.length > 40) return null
        val written = shape.matchEntire(t)?.groupValues?.get(1)?.trim() ?: return null
        return plain(written) ?: withNote(written) ?: bracketed(t)
    }

    // the pattern for a part: a line that is only a text in brackets, and not chords ("[Am]", "[G D]" stay chords)
    private val wholeBracket = Regex("""^\[([^\[\]]+)]$""")

    private fun bracketed(line: String): Header? {
        val name = wholeBracket.matchEntire(line)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (name.split(' ').filter { it.isNotEmpty() }.all(Chords::isChord)) return null
        return Header(Kind.OTHER, "", "", name)
    }

    // "Verse 1 (repeat)", "Intro — Base 2x", "Fim - Repete 3x": the part, with the note kept (user, 01/11)
    // the outer shape already took the closing ")" with the brackets around the title
    private val noteInParens = Regex("""^(.+?)\s*\(([^()]+)\)?$""")
    private val noteAfterDash = Regex("""^(.+?)\s+([—–-]\s+.+)$""")

    private fun withNote(written: String): Header? {
        noteInParens.matchEntire(written)?.let { m ->
            val note = "(" + m.groupValues[2].trim() + ")"
            return plain(m.groupValues[1].trim())?.copy(original = m.groupValues[1].trim() + " " + note, note = note)
        }
        val m = noteAfterDash.matchEntire(written) ?: return null
        return plain(m.groupValues[1].trim())?.copy(original = written, note = m.groupValues[2].trim())
    }

    private fun plain(written: String): Header? {
        val inner = written.replace(brackets, " ").trim()
        // "[Tab - Intro]" is the Intro of a tab (Cifra Club's title): the "tab" word goes, the section stays (31/10)
        val key = TextKey.of(inner).replace(tabPrefix, "")
        val m = tail.matchEntire(key) ?: return null
        val word = m.groupValues[1].trim()
        val kind = Kind.entries.firstOrNull { word in it.keys } ?: return null
        val number = when {
            word.startsWith("primeira") -> "1"
            word.startsWith("segunda") -> "2"
            word.startsWith("terceira") -> "3"
            else -> m.groupValues[2]
        }
        val repeat = digits.find(m.groupValues[3])?.let { it.value + "x" }.orEmpty()
        return Header(kind, number, repeat, inner)
    }

    /** Name shown in the chosen language ("as written" = without brackets and colons). */
    fun label(h: Header, lang: SectionLang): String {
        if (lang == SectionLang.ORIGINAL || h.kind == Kind.OTHER) return h.original
        return buildString {
            append(if (lang == SectionLang.EN) h.kind.en else h.kind.pt)
            if (h.number.isNotEmpty()) append(' ').append(h.number)
            if (h.repeat.isNotEmpty()) append("  ").append(h.repeat)
            if (h.note.isNotEmpty()) append(' ').append(h.note)
        }
    }

    /** [name] (the section name in the app's language) with the original title's number and repeat. */
    fun label(h: Header, name: String): String = if (h.kind == Kind.OTHER) h.original else buildString {
        append(name)
        if (h.number.isNotEmpty()) append(' ').append(h.number)
        if (h.repeat.isNotEmpty()) append("  ").append(h.repeat)
        if (h.note.isNotEmpty()) append(' ').append(h.note)
    }

    fun parse(text: String): List<Line> = text.split('\n').map { Line(it, detect(it)) }

    /** Rewrites section titles in the "[Chorus]", "[Verse 2]" format ("Standardize" button). */
    fun normalize(text: String, lang: SectionLang): String {
        val target = if (lang == SectionLang.ORIGINAL) SectionLang.PT else lang
        return parse(text).joinToString("\n") { line ->
            line.header?.let { "[" + label(it, target).replace("  ", " ") + "]" } ?: line.text
        }
    }
}
