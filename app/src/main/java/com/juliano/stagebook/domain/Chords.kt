package com.juliano.stagebook.domain

/**
 * Cifra em ChordPro (padrão aberto, chordpro.org), o formato em que o Stagebook guarda as músicas: o acorde
 * vai entre colchetes antes da sílaba (`[G]Hoje eu [D]vou`) e as informações em diretivas (`{key: G}`,
 * `{capo: 2}`). Aqui ficam as regras puras: reconhecer acorde, converter a cifra com acordes na linha de
 * cima, transpor, tom com capo e tirar os acordes (perfil "só letra").
 *
 * `[Refrão]` também usa colchetes (título de seção, ver [Sections]): só é acorde o que tem forma de acorde.
 */
object Chords {
    private val sharps = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val flats = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
    // tons escritos com bemóis (maiores e menores); os outros com sustenidos
    private val flatKeys = setOf("F", "Bb", "Eb", "Ab", "Db", "Gb", "Cb", "Dm", "Gm", "Cm", "Fm", "Bbm", "Ebm", "Abm")

    // nota, acidente, o resto do acorde (m, 7, maj7, sus4, add9, dim, aug, °, +, 7M, (9), (b5)…) e o baixo
    private val chord = Regex("""([A-G])([#b♯♭]?)((?:maj|min|dim|aug|sus|add|m|M|°|º|\+|-|\d|\(|\)|#|b|♯|♭|,)*)(?:/([A-G])([#b♯♭]?))?""")
    private val bracket = Regex("""\[([^\[\]]*)]""")
    private val directive = Regex("""^\s*\{\s*([A-Za-z_][A-Za-z_-]*)\s*(?::\s*(.*?))?\s*}\s*$""")
    // marcas que podem estar numa linha de acordes sem fazer dela uma linha de letra
    private val chordLineExtras = Regex("""\||/|%|-+|\(?\d+\s*x\)?|\(?x\s*\d+\)?""", RegexOption.IGNORE_CASE)

    /** O texto inteiro é um acorde? (`G`, `F#m7/E`, `Cm7(b5)`, `G7M`, `Bb`) */
    fun isChord(s: String): Boolean = chord.matchEntire(s.trim()) != null

    // ---- diretivas ----

    fun isDirective(line: String): Boolean = directive.matchEntire(line) != null

    /** Valor de uma diretiva (`key`, `capo`…), ou null. */
    fun directive(text: String, name: String): String? = text.lineSequence()
        .mapNotNull { directive.matchEntire(it) }
        .firstOrNull { it.groupValues[1].equals(name, ignoreCase = true) }
        ?.groupValues?.get(2)?.trim()

    /** Grava (ou tira, com valor vazio) uma diretiva; nova vai para o começo do texto. */
    fun setDirective(text: String, name: String, value: String): String {
        val lines = text.split('\n').toMutableList()
        val i = lines.indexOfFirst { directive.matchEntire(it)?.groupValues?.get(1).equals(name, ignoreCase = true) }
        val line = "{$name: $value}"
        when {
            i >= 0 && value.isEmpty() -> lines.removeAt(i)
            i >= 0 -> lines[i] = line
            value.isNotEmpty() -> lines.add(0, line)
        }
        return lines.joinToString("\n")
    }

    private val meta = setOf("key", "capo", "tempo")

    /** BPM da música (`{tempo: 120}` do ChordPro); 0 = sem. */
    fun tempo(text: String): Int = directive(text, "tempo")?.filter(Char::isDigit)?.toIntOrNull()?.takeIf { it in MIN_TEMPO..MAX_TEMPO } ?: 0

    const val MIN_TEMPO = 20
    const val MAX_TEMPO = 300
    const val DEFAULT_TEMPO = 100   // metrônomo sem BPM na música

    private fun isMeta(line: String) = directive.matchEntire(line)?.groupValues?.get(1)?.lowercase() in meta

    /** A letra sem as linhas de tom e capo (no editor elas ficam nos dados da música, não no campo da letra). */
    fun hideMeta(text: String): String = text.split('\n').filterNot(::isMeta).joinToString("\n")

    /** A letra editada (sem tom e capo) com o tom e o capo de [from] de volta no começo. */
    fun withMeta(edited: String, from: String): String {
        var t = hideMeta(edited)
        tempo(from).takeIf { it > 0 }?.let { t = setDirective(t, "tempo", it.toString()) }
        capo(from).takeIf { it > 0 }?.let { t = setDirective(t, "capo", it.toString()) }
        key(from).takeIf { it.isNotEmpty() }?.let { t = setDirective(t, "key", it) }
        return t
    }

    fun key(text: String): String = directive(text, "key").orEmpty()

    // acordes que o usuário tirou da lista de acordes do palco (extensão do ChordPro: diretivas x_ são dos apps)
    private const val HIDE = "x_hide_chords"

    fun hiddenChords(text: String): Set<String> =
        directive(text, HIDE).orEmpty().split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()

    fun setHiddenChords(text: String, chords: Set<String>): String = setDirective(text, HIDE, chords.joinToString(" "))

    fun capo(text: String): Int = directive(text, "capo")?.toIntOrNull()?.coerceIn(0, 12) ?: 0

    // ---- acordes na linha de cima → ChordPro ----

    /** Linha só de acordes (e marcas como `|`, `(2x)`), com pelo menos um acorde. Aceita rótulo antes: `Intro: G D`, `[Intro] G D`. */
    fun isChordLine(line: String): Boolean {
        val tokens = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return false
        val body = if (tokens.size > 1 && isLabel(tokens[0])) tokens.drop(1) else tokens
        return body.any(::isChord) && body.all { isChord(it) || chordLineExtras.matches(it) }
    }

    private fun isLabel(t: String) =
        (t.endsWith(":") && !isChord(t.dropLast(1))) || (t.startsWith("[") && t.endsWith("]") && !isChord(t.drop(1).dropLast(1)))

    /**
     * Converte cifra com os acordes na linha de cima (Cifra Club, texto colado) para ChordPro: cada acorde
     * entra na letra da linha de baixo, na mesma coluna. Linha de acordes sem letra embaixo (intro, solo)
     * vira uma linha só de acordes. O que já está em ChordPro passa igual.
     */
    fun fromChordsOverLyrics(text: String): String {
        val lines = text.replace("\t", "    ").split('\n')
        val out = ArrayList<String>(lines.size)
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (!isChordLine(line)) { out += line; i++; continue }
            // "[Intro] G D" (rótulo de seção na frente): o título numa linha e os acordes na de baixo
            val first = line.trim().substringBefore(' ')
            if (first.startsWith("[") && Sections.detect(first) != null && line.trim().length > first.length) {
                out += first
                out += chordsOnly(line.trim().removePrefix(first))
                i++
                continue
            }
            val next = lines.getOrNull(i + 1)
            val lyric = next != null && next.isNotBlank() && !isChordLine(next) && !isDirective(next) && Sections.detect(next) == null
            out += if (lyric) merge(line, next!!) else chordsOnly(line)
            i += if (lyric) 2 else 1
        }
        return out.joinToString("\n")
    }

    private fun positions(chordLine: String): List<Pair<Int, String>> =
        Regex("\\S+").findAll(chordLine).map { it.range.first to it.value }.toList()

    private fun merge(chordLine: String, lyric: String): String {
        val sb = StringBuilder(lyric.trimEnd())
        positions(chordLine).reversed().forEach { (col, token) ->
            val mark = if (isChord(token)) "[$token]" else return@forEach
            if (col >= sb.length) {
                // acorde depois do fim da letra: fica no fim, separado
                while (sb.length < col) sb.append(' ')
                sb.append(mark)
            } else {
                sb.insert(col, mark)
            }
        }
        return sb.toString()
    }

    private fun chordsOnly(chordLine: String): String =
        positions(chordLine).joinToString(" ") { (_, t) -> if (isChord(t)) "[$t]" else t }

    // ---- transpor ----

    private fun index(note: String): Int {
        val n = note.replace('♯', '#').replace('♭', 'b')
        val i = sharps.indexOf(n).takeIf { it >= 0 } ?: flats.indexOf(n)
        return when {
            i >= 0 -> i
            n == "Cb" -> 11
            n == "Fb" -> 4
            n == "E#" -> 5
            n == "B#" -> 0
            else -> -1
        }
    }

    private fun name(i: Int, useFlats: Boolean) = (if (useFlats) flats else sharps)[((i % 12) + 12) % 12]

    /** O acorde [semitones] meios-tons acima (negativo = abaixo). [useFlats] null = o estilo do próprio acorde. */
    fun transposeChord(c: String, semitones: Int, useFlats: Boolean? = null): String {
        val m = chord.matchEntire(c.trim()) ?: return c
        val (root, acc, rest, bass, bassAcc) = m.destructured
        val flat = useFlats ?: (acc == "b" || acc == "♭")
        val r = index(root + acc).takeIf { it >= 0 } ?: return c
        val out = StringBuilder(name(r + semitones, flat)).append(rest)
        if (bass.isNotEmpty()) {
            val b = index(bass + bassAcc)
            out.append('/').append(if (b >= 0) name(b + semitones, flat) else bass + bassAcc)
        }
        return out.toString()
    }

    // os nomes de tom que os músicos usam, meio tom a meio tom (pedido do usuário, 29/10): o − e o + do editor andam
    // nesta ordem, e os acordes seguem a armadura do tom (bemóis nos tons de bemol)
    private val majorKeys = listOf("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
    private val minorKeys = listOf("Cm", "C#m", "Dm", "Ebm", "Em", "Fm", "F#m", "Gm", "G#m", "Am", "Bbm", "Bm")

    /** O tom [semitones] meios-tons acima, com o nome de costume (D + 1 = Eb, F + 1 = F#, Am + 1 = Bbm). */
    fun transposeKey(key: String, semitones: Int): String {
        val m = chord.matchEntire(key.trim()) ?: return key
        val root = index(m.groupValues[1] + m.groupValues[2]).takeIf { it >= 0 } ?: return key
        val minor = quality(m.groupValues[3]) == Quality.MINOR
        val i = ((root + semitones) % 12 + 12) % 12
        return (if (minor) minorKeys else majorKeys)[i]
    }

    /** Transpõe todos os acordes da música e o tom guardado (`{key}`), com bemóis nos tons de bemol. */
    fun transpose(text: String, semitones: Int): String {
        if (semitones % 12 == 0) return text
        val oldKey = key(text)
        val newKey = if (oldKey.isNotEmpty()) transposeKey(oldKey, semitones) else ""
        // o tom novo decide bemol x sustenido; sem tom, cada acorde mantém o seu estilo
        val useFlats = if (newKey.isNotEmpty()) newKey in flatKeys else null   // o nome do tom já é o de costume
        val body = text.split('\n').joinToString("\n") { line ->
            if (isDirective(line)) line
            else bracket.replace(line) { b -> if (isChord(b.groupValues[1])) "[" + transposeChord(b.groupValues[1], semitones, useFlats) + "]" else b.value }
        }
        return if (newKey.isNotEmpty()) setDirective(body, "key", newKey) else body
    }

    // ---- tom pelos acordes (importação sem {key}) ----

    private enum class Quality { MAJOR, MINOR, DIM }

    private fun quality(rest: String) = when {
        rest.startsWith("dim") || rest.startsWith("°") || rest.startsWith("º") || rest.startsWith("m7b5") -> Quality.DIM
        rest.startsWith("m") && !rest.startsWith("maj") -> Quality.MINOR
        else -> Quality.MAJOR
    }

    // graus do campo harmônico: maior I ii iii IV V vi vii°; menor (natural, com o V maior da harmônica) i ii° III iv v V VI VII
    private val majorField = listOf(0 to Quality.MAJOR, 2 to Quality.MINOR, 4 to Quality.MINOR, 5 to Quality.MAJOR, 7 to Quality.MAJOR, 9 to Quality.MINOR, 11 to Quality.DIM)
    private val minorField = listOf(0 to Quality.MINOR, 2 to Quality.DIM, 3 to Quality.MAJOR, 5 to Quality.MINOR, 7 to Quality.MINOR, 7 to Quality.MAJOR, 8 to Quality.MAJOR, 10 to Quality.MAJOR)

    /**
     * O tom provável pelos acordes (ex.: "G", "Em"); vazio sem acordes. Cada tom (12 maiores e 12 menores) ganha um ponto por
     * acorde do seu campo harmônico; o primeiro e o último acorde contam mais quando são a tônica. Empate: o maior.
     */
    fun detectKey(text: String): String {
        val chords = bracket.findAll(fromChordsOverLyrics(text)).mapNotNull { chord.matchEntire(it.groupValues[1].trim()) }
            .mapNotNull { m -> index(m.groupValues[1] + m.groupValues[2]).takeIf { it >= 0 }?.let { it to quality(m.groupValues[3]) } }
            .toList()
        if (chords.isEmpty()) return ""
        var best = "" ; var bestScore = -1.0
        for (minor in listOf(false, true)) for (tonic in 0 until 12) {
            val field = if (minor) minorField else majorField
            val tonicChord = tonic to if (minor) Quality.MINOR else Quality.MAJOR
            var score = chords.count { (r, q) -> field.any { (deg, fq) -> (tonic + deg) % 12 == r && fq == q } }.toDouble()
            if (chords.first() == tonicChord) score += 2.0
            if (chords.last() == tonicChord) score += 1.5
            if (score > bestScore) {
                bestScore = score
                best = (if (minor) minorKeys else majorKeys)[tonic]   // o nome de costume do tom
            }
        }
        return best
    }

    /** Sem {key}: grava o tom calculado pelos acordes (só na importação; o que o usuário escrever depois prevalece). */
    fun withDetectedKey(text: String): String =
        if (key(text).isNotEmpty()) text else detectKey(text).takeIf { it.isNotEmpty() }?.let { setDirective(text, "key", it) } ?: text

    /** Tom que soa com o capo: os desenhos ficam, o som sobe [capo] casas (G com capo 2 soa A). */
    fun soundingKey(key: String, capo: Int): String = if (key.isEmpty() || capo == 0) key else transposeChord(key, capo)

    // ---- perfil "só letra" ----

    /** A letra sem acordes: tira os `[C]`, as linhas só de acordes e as diretivas. Títulos de seção ficam. */
    fun stripChords(text: String): String = text.split('\n').mapNotNull { line ->
        if (isDirective(line)) return@mapNotNull null
        val hadChord = bracket.findAll(line).any { isChord(it.groupValues[1]) }
        val clean = bracket.replace(line) { b -> if (isChord(b.groupValues[1])) "" else b.value }
        if (hadChord && clean.isBlank()) null else clean.replace(Regex(" {2,}"), " ").trimEnd()
    }.joinToString("\n")

    /** A música tem algum acorde? */
    fun hasChords(text: String): Boolean = bracket.findAll(text).any { isChord(it.groupValues[1]) }
}
