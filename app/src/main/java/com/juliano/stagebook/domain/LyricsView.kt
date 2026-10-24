package com.juliano.stagebook.domain

/**
 * Edição no perfil "só letra": o editor mostra a letra sem a cifra ([view]) e cada mudança volta para a música
 * com a cifra preservada ([merge]). Quem só canta pode corrigir a letra sem apagar os acordes de ninguém.
 *
 * Linhas escondidas (`{key}`, `{capo}`, linhas só de acordes) ficam onde estavam; seções e comentários do
 * ChordPro (`{start_of_chorus}`, `{c: Solo}`) são da letra e aparecem. Linha igual mantém tudo; linha
 * mudada mantém os acordes cujo trecho antes (ou depois) dele não mudou; linha nova fica sem acorde.
 */
object LyricsView {
    private val bracket = Regex("""\[([^\[\]]*)]""")

    /** A linha sem os acordes (o resto, espaços inclusive, fica igual: as posições batem com [chordsAt]). */
    private fun plain(line: String) = bracket.replace(line) { if (Chords.isChord(it.groupValues[1])) "" else it.value }

    // diretivas de estrutura (seções, comentários) são da letra e ficam à vista; o resto ({key}, {capo}, {define}…) é cifra
    private val structure = Regex("""^\s*\{\s*(start_of_\w+|end_of_\w+|so[cvb]|eo[cvb]|comment|c|ci)\s*(:.*)?}\s*$""", RegexOption.IGNORE_CASE)

    private fun hidden(line: String) =
        (Chords.isDirective(line) && !structure.matches(line)) || (Chords.hasChords(line) && plain(line).isBlank())

    /** Acordes da linha e a posição de cada um na linha sem acordes. */
    private fun chordsAt(line: String): List<Pair<Int, String>> {
        val out = ArrayList<Pair<Int, String>>()
        var removed = 0
        bracket.findAll(line).forEach { m ->
            if (!Chords.isChord(m.groupValues[1])) return@forEach
            out += (m.range.first - removed) to m.groupValues[1]
            removed += m.value.length
        }
        return out
    }

    fun view(text: String): String = text.split('\n').filterNot(::hidden).joinToString("\n") { plain(it) }

    fun merge(original: String, edited: String): String {
        val lines = original.split('\n')
        val visible = lines.indices.filterNot { hidden(lines[it]) }
        val old = visible.map { plain(lines[it]) }
        val new = edited.split('\n')
        val pairs = lcs(old, new)
        val out = ArrayList<String>(lines.size + 4)
        var nextLine = 0   // próxima linha da música original ainda não escrita (para as escondidas)
        fun hiddenUntil(line: Int) {
            for (k in nextLine until line) if (hidden(lines[k])) out += lines[k]
            nextLine = maxOf(nextLine, line)
        }
        var oi = 0
        var nj = 0
        for ((pi, pj) in pairs + (old.size to new.size)) {
            // trecho mudado entre duas linhas iguais: a k-ésima antiga vira a k-ésima nova
            val gapOld = oi until pi
            val gapNew = nj until pj
            gapNew.forEachIndexed { k, j ->
                val o = gapOld.elementAtOrNull(k)
                if (o != null) {
                    hiddenUntil(visible[o])
                    nextLine = visible[o] + 1
                    out += reinsert(lines[visible[o]], new[j])
                } else {
                    out += new[j]
                }
            }
            // linhas antigas apagadas: as escondidas antes delas ficam
            gapOld.drop(gapNew.count()).forEach { o -> hiddenUntil(visible[o]); nextLine = visible[o] + 1 }
            if (pi < old.size) {
                hiddenUntil(visible[pi])
                out += lines[visible[pi]]
                nextLine = visible[pi] + 1
            }
            oi = pi + 1
            nj = pj + 1
        }
        hiddenUntil(lines.size)
        return out.joinToString("\n")
    }

    /** A linha nova com os acordes da antiga cujo trecho antes (ou depois) dele continua igual. */
    private fun reinsert(oldLine: String, newText: String): String {
        val oldPlain = plain(oldLine)
        val keep = chordsAt(oldLine).mapNotNull { (at, chord) ->
            when {
                newText.startsWith(oldPlain.substring(0, at)) -> at to chord
                newText.endsWith(oldPlain.substring(at)) -> (newText.length - (oldPlain.length - at)) to chord
                else -> null
            }
        }
        val sb = StringBuilder(newText)
        keep.sortedByDescending { it.first }.forEach { (at, chord) -> sb.insert(at.coerceIn(0, sb.length), "[$chord]") }
        return sb.toString()
    }

    /** Pares (antiga, nova) das linhas iguais, na ordem (maior subsequência comum). */
    private fun lcs(a: List<String>, b: List<String>): List<Pair<Int, Int>> {
        val t = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            t[i][j] = if (a[i] == b[j]) t[i + 1][j + 1] + 1 else maxOf(t[i + 1][j], t[i][j + 1])
        }
        val out = ArrayList<Pair<Int, Int>>()
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] == b[j] -> { out += i to j; i++; j++ }
                t[i + 1][j] >= t[i][j + 1] -> i++
                else -> j++
            }
        }
        return out
    }
}
