package com.juliano.stagebook.domain

/**
 * O que mudou entre duas versões de uma letra, linha a linha (para o usuário decidir se fica com a
 * atualização). Letras têm dezenas de linhas: a tabela da maior subsequência comum é barata.
 */
object LineDiff {
    enum class Kind { SAME, ADDED, REMOVED, GAP }   // GAP = trecho igual omitido ("…")

    data class Line(val kind: Kind, val text: String)

    /** Todas as linhas, marcadas como iguais, novas ou retiradas, na ordem da letra. */
    fun of(before: String, after: String): List<Line> {
        val a = before.replace("\r", "").lines()
        val b = after.replace("\r", "").lines()
        // lcs[i][j] = tamanho da maior sequência comum entre a[i..] e b[j..]
        val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
        val out = ArrayList<Line>()
        var i = 0
        var j = 0
        while (i < a.size || j < b.size) {
            when {
                i < a.size && j < b.size && a[i] == b[j] -> { out += Line(Kind.SAME, a[i]); i++; j++ }
                j < b.size && (i == a.size || lcs[i][j + 1] >= lcs[i + 1][j]) -> { out += Line(Kind.ADDED, b[j]); j++ }
                else -> { out += Line(Kind.REMOVED, a[i]); i++ }
            }
        }
        return out
    }

    /** Só as mudanças, com [context] linhas iguais em volta; trechos iguais omitidos viram um GAP. Iguais: vazio. */
    fun changes(before: String, after: String, context: Int = 1): List<Line> {
        val all = of(before, after)
        if (all.all { it.kind == Kind.SAME }) return emptyList()
        val near = BooleanArray(all.size)
        all.forEachIndexed { k, l ->
            if (l.kind != Kind.SAME) for (n in (k - context).coerceAtLeast(0)..(k + context).coerceAtMost(all.lastIndex)) near[n] = true
        }
        val out = ArrayList<Line>()
        all.forEachIndexed { k, l ->
            when {
                near[k] -> if (!(l.kind == Kind.SAME && l.text.isBlank() && out.lastOrNull()?.kind != Kind.SAME)) out += l
                out.lastOrNull()?.kind != Kind.GAP -> out += Line(Kind.GAP, "")
            }
        }
        return out   // GAP no começo/fim também: mostra que há letra igual antes/depois
    }
}
