package br.app.stagebook.domain

/**
 * Tablatura dentro da cifra (T22). Guardada como `{start_of_tab}`…`{end_of_tab}` do ChordPro; linhas soltas com cara de
 * tab (`e|---0---3---|`) também contam. [shift] sobe/desce as casas mantendo as colunas alinhadas: transpor o tom e mudar o
 * capo mexem nelas (decisão do usuário, 29/10: a tab fica em casas sem capo). Casa que ficaria negativa sobe uma oitava.
 */
object Tablature {
    const val START = "{start_of_tab}"
    const val END = "{end_of_tab}"

    private val startDirective = Regex("""^\s*\{\s*(start_of_tab|sot)\s*(:.*)?}\s*$""", RegexOption.IGNORE_CASE)
    private val endDirective = Regex("""^\s*\{\s*(end_of_tab|eot)\s*}\s*$""", RegexOption.IGNORE_CASE)
    // nome da corda (opcional) + barra, e depois só traços, números e as marcas de técnica (h p b r / \ ~ x)
    private val tabLine = Regex("""^\s*([A-Ga-g][#b]?\s*)?[|:][-0-9|hpbrxHPBRX/\\~().*\s]+$""")

    fun isStart(line: String) = startDirective.matches(line)
    fun isEnd(line: String) = endDirective.matches(line)

    /** Uma linha com cara de tablatura (precisa de traços: "e|---0---|"). */
    fun isTabLine(line: String): Boolean = tabLine.matches(line) && line.count { it == '-' } >= 3

    /** Para cada linha do texto: se ela é de tablatura (dentro do bloco ou com cara de tab). */
    fun tabLines(text: String): List<Boolean> {
        var inBlock = false
        return text.split('\n').map { line ->
            when {
                isStart(line) -> { inBlock = true; false }
                isEnd(line) -> { inBlock = false; false }
                else -> inBlock || isTabLine(line)
            }
        }
    }

    fun hasTab(text: String): Boolean = tabLines(text).any { it }

    /** Sobe ([semitones] > 0) ou desce as casas de toda tablatura do texto, sem tirar as colunas do lugar. */
    fun shift(text: String, semitones: Int): String {
        if (semitones == 0) return text
        val flags = tabLines(text)
        return text.split('\n').mapIndexed { i, line -> if (flags[i]) shiftLine(line, semitones) else line }.joinToString("\n")
    }

    /** Uma linha de tab com as casas deslocadas; o nome da corda no começo (e, B, G…) não é casa. */
    fun shiftLine(line: String, semitones: Int): String {
        val bar = line.indexOfFirst { it == '|' || it == ':' }.coerceAtLeast(0)
        val out = StringBuilder(line.substring(0, bar))
        var i = bar
        while (i < line.length) {
            val c = line[i]
            if (!c.isDigit()) { out.append(c); i++; continue }
            var j = i
            while (j < line.length && line[j].isDigit()) j++
            var fret = line.substring(i, j).toInt() + semitones
            while (fret < 0) fret += 12
            val now = fret.toString()
            var width = j - i
            out.append(now)
            // casa com mais algarismos come os traços de depois; com menos, devolve traços (as colunas não andam)
            while (now.length > width && j < line.length && line[j] == '-') { j++; width++ }
            repeat(width - now.length) { out.append('-') }
            i = j
        }
        return out.toString()
    }

    /** Põe as linhas de tab soltas entre `{start_of_tab}` e `{end_of_tab}` (o que veio da importação). */
    fun wrap(text: String): String {
        val lines = text.split('\n')
        val flags = lines.map(::isTabLine)
        val out = ArrayList<String>()
        lines.forEachIndexed { i, line ->
            val prev = i > 0 && flags[i - 1]
            val next = i < lines.lastIndex && flags[i + 1]
            if (flags[i] && !prev) out += START
            out += line
            if (flags[i] && !next) out += END
        }
        return out.joinToString("\n")
    }

    /** Tira toda tablatura (importar sem a tab). */
    fun strip(text: String): String {
        val flags = tabLines(text)
        return text.split('\n').filterIndexed { i, line -> !flags[i] && !isStart(line) && !isEnd(line) }.joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
    }
}
