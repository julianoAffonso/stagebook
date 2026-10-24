package com.juliano.stagebook.domain

/**
 * Cifra importada de um site (Cifra Club e parecidos): o texto com os acordes na linha de cima vira ChordPro,
 * com o tom e o capotraste da página em `{key}`/`{capo}`. Tablaturas (linhas `E|--3--|`) ficam de fora.
 */
object ChordImport {
    private val tabLine = Regex("""^\s*[A-Ga-g][#b]?\s*\|[-0-9hpbr/\\|~x\s]*$""")
    private val trailingSpace = Regex("\\s+$")
    private val manyBlank = Regex("\n{3,}")
    private val capoText = Regex("""(?i)capotraste\s+na\s+(\d{1,2})""")
    private val keyText = Regex("""(?i)\btom:\s*([A-G][#b]?m?)\b""")

    /** A página tem cifra? (duas ou mais linhas só de acordes) */
    fun hasChords(text: String): Boolean = text.lineSequence().count(Chords::isChordLine) >= 2

    /**
     * A cifra da página em ChordPro. [key] e [capo] vêm da página (texto "Tom: G", "Capotraste na 2ª casa"),
     * e na falta deles são procurados no próprio texto.
     */
    fun toChordPro(text: String, key: String = "", capo: String = ""): String {
        // as linhas "Tom: G" e "Capotraste na 2ª casa" viram diretivas: saem antes de converter (senão o G da linha
        // do tom ia parar em cima da letra de baixo)
        val lines = text.replace("\r", "").split('\n').map { it.replace(trailingSpace, "") }
            .filterNot { tabLine.matches(it) || keyText.matches(it.trim()) || (capoText.containsMatchIn(it) && it.length < 40) }
        var body = Chords.fromChordsOverLyrics(lines.joinToString("\n")).replace(manyBlank, "\n\n").trim()
        val k = key.trim().ifEmpty { keyText.find(text)?.groupValues?.get(1).orEmpty() }
        val c = capoText.find(capo)?.groupValues?.get(1)?.toIntOrNull() ?: capo.trim().toIntOrNull() ?: capoText.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        if (c in 1..12) body = Chords.setDirective(body, "capo", c.toString())
        if (Chords.isChord(k)) body = Chords.setDirective(body, "key", k)
        return body
    }
}
