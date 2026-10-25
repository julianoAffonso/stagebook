package com.juliano.stagebook.domain

/**
 * Mesma música, versões diferentes (chegou da banda ou pelas 3 palavras): "letra de quem canta, acorde de quem toca".
 * Quem recebe escolhe música a música; aceitar a recebida nunca apaga a cifra de quem recebe.
 */
object SongMerge {
    enum class Kind { REMOVED, ADDED }

    /** Uma linha que muda na letra: sai a minha ([Kind.REMOVED]) ou entra a recebida ([Kind.ADDED]). */
    data class Change(val kind: Kind, val text: String)

    private fun norm(text: String) = text.replace("\r", "").split('\n').map { it.trimEnd() }.dropLastWhile { it.isEmpty() }

    /** A mesma versão (espaço no fim da linha e linha em branco no fim não contam). */
    fun same(a: String, b: String) = norm(a) == norm(b)

    /**
     * A versão recebida aceita: se ela veio só com a letra e a minha tem cifra, a letra nova entra **com os meus acordes**
     * (nos trechos que não mudaram); senão, a recebida inteira.
     */
    fun accept(mine: String, incoming: String): String =
        if (Chords.hasChords(mine) && !Chords.hasChords(incoming)) LyricsView.merge(mine, LyricsView.view(incoming)) else incoming

    /** As linhas da letra (sem os acordes) que mudam da minha para a recebida, na ordem. Vazio = só a cifra muda. */
    fun changes(mine: String, incoming: String): List<Change> {
        val a = LyricsView.view(mine).split('\n').map { it.trimEnd() }
        val b = LyricsView.view(incoming).split('\n').map { it.trimEnd() }
        val t = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            t[i][j] = if (a[i] == b[j]) t[i + 1][j + 1] + 1 else maxOf(t[i + 1][j], t[i][j + 1])
        }
        val out = ArrayList<Change>()
        var i = 0
        var j = 0
        while (i < a.size || j < b.size) {
            when {
                i < a.size && j < b.size && a[i] == b[j] -> { i++; j++ }
                j < b.size && (i == a.size || t[i][j + 1] > t[i + 1][j]) -> { if (b[j].isNotBlank()) out += Change(Kind.ADDED, b[j]); j++ }
                else -> { if (a[i].isNotBlank()) out += Change(Kind.REMOVED, a[i]); i++ }
            }
        }
        return out
    }
}
