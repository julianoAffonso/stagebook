package com.juliano.stagebook.domain

/**
 * Cifra importada de um site (Cifra Club e parecidos): o texto com os acordes na linha de cima vira ChordPro,
 * com o tom e o capotraste da página em `{key}`/`{capo}`. Tablaturas (linhas `E|--3--|`) ficam de fora.
 */
object ChordImport {
    private val tabLine = Regex("""^\s*[A-Ga-g][#b]?\s*\|[-0-9hpbr/\\|~x\s]*$""")
    private val trailingSpace = Regex("\\s+$")
    private val manyBlank = Regex("\n{3,}")
    // tom e capo como os sites escrevem nas 4 línguas: "Tom: G", "Key: G", "Tono: G", "Tonalità: G";
    // "Capotraste na 2ª casa", "Capo 2", "Capo: 2nd fret", "Cejilla en el 2º traste", "Capotasto al 2º tasto"
    private val capoText = Regex("""(?i)\b(?:capotraste\s+na|capotasto\s+(?:al|sul)|cejilla(?:\s+en(?:\s+el)?)?|capo(?:\s+on)?)\s*:?\s*(\d{1,2})""")
    private val keyText = Regex("""(?i)\b(?:tom|key|tono|tonalit[àa])\s*:\s*([A-G][#b]?m?)(?![\w#])""")

    /** Tom e capo que a página mostra (para guardar também no perfil só letra). */
    fun keyOf(vararg texts: String): String = texts.firstNotNullOfOrNull { t -> keyText.find(t)?.groupValues?.get(1)?.takeIf(Chords::isChord) ?: t.trim().takeIf(Chords::isChord) }.orEmpty()

    /** BPM: o da página, ou uma linha "BPM 120"/"Tempo: 120"/"{tempo: 120}" no próprio texto; 0 = não veio. */
    fun bpmOf(page: String, text: String): Int {
        val fromText = bpmText.find(text)?.let { m -> m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } }
        return listOf(page.trim(), fromText.orEmpty()).firstNotNullOfOrNull { it.toIntOrNull()?.takeIf { v -> v in Chords.MIN_TEMPO..Chords.MAX_TEMPO } } ?: 0
    }

    private val bpmText = Regex("""(?im)^\s*(?:\{\s*tempo\s*:\s*(\d{2,3})\s*\}|(?:bpm|tempo)\s*[:=]?\s*(\d{2,3})|(\d{2,3})\s*bpm)\s*$""")

    fun capoOf(vararg texts: String): Int = texts.firstNotNullOfOrNull { t -> capoText.find(t)?.groupValues?.get(1)?.toIntOrNull() ?: t.trim().toIntOrNull() }?.takeIf { it in 1..12 } ?: 0

    /** A página tem cifra? (duas ou mais linhas só de acordes) */
    fun hasChords(text: String): Boolean = text.lineSequence().count(Chords::isChordLine) >= 2

    /**
     * A cifra da página em ChordPro. [key] e [capo] vêm da página (texto "Tom: G", "Capotraste na 2ª casa"),
     * e na falta deles são procurados no próprio texto.
     */
    fun toChordPro(text: String, key: String = "", capo: String = ""): String {
        // as linhas "Tom: G" e "Capotraste na 2ª casa" viram diretivas: saem antes de converter (senão o G da linha
        // do tom ia parar em cima da letra de baixo)
        val all = text.replace("\r", "").split('\n').map { it.replace(trailingSpace, "") }
        // blocos de tablatura marcados ({start_of_tab}…{end_of_tab}, T22) passam como estão; o resto é convertido
        val inTab = Tablature.tabLines(all.joinToString("\n"))
        val parts = ArrayList<String>()
        val plain = ArrayList<String>()
        fun flush() {
            if (plain.isEmpty()) return
            parts += Chords.fromChordsOverLyrics(plain.filterNot { tabLine.matches(it) || keyText.matches(it.trim()) || (capoText.containsMatchIn(it) && it.length < 40) }.joinToString("\n"))
            plain.clear()
        }
        var block = false
        all.forEachIndexed { i, line ->
            when {
                Tablature.isStart(line) -> { flush(); block = true; parts += line }
                Tablature.isEnd(line) -> { block = false; parts += line }
                block && inTab[i] -> parts += line
                else -> plain += line
            }
        }
        flush()
        var body = parts.joinToString("\n").replace(manyBlank, "\n\n").trim()
        val k = keyOf(key, text)
        val c = capoOf(capo, text)
        if (c in 1..12) body = Chords.setDirective(body, "capo", c.toString())
        if (Chords.isChord(k)) body = Chords.setDirective(body, "key", k)
        return body
    }
}
