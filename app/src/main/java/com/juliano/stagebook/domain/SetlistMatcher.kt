package com.juliano.stagebook.domain

import com.juliano.stagebook.data.model.Song

/** Resultado de cada música do QR contra a biblioteca deste aparelho. */
sealed interface QrMatch {
    /** Mesmo título (e artista, quando há mais de uma com o mesmo título). */
    data class Found(val song: Song) : QrMatch
    /** Nada com o mesmo título, ou títulos iguais sem como desempatar: o usuário escolhe ou pula. */
    data class Similar(val candidates: List<Song>) : QrMatch
    data object Missing : QrMatch
}

/**
 * Casa cada música do QR com a biblioteca pela chave do título (a mesma regra da biblioteca); o artista desempata
 * títulos iguais ("It's My Life" do Bon Jovi e do Talk Talk). Sem título igual, sugere as parecidas — nunca escolhe
 * sozinha uma música diferente.
 */
object SetlistMatcher {
    private const val SIMILAR_MIN = 0.75
    private const val MAX_CANDIDATES = 3

    fun match(entry: QrPayload.Entry, library: List<Song>): QrMatch {
        val kt = TextKey.of(entry.title)
        val sameTitle = library.filter { TextKey.of(it.title) == kt }
        if (sameTitle.size == 1) return QrMatch.Found(sameTitle[0])
        if (sameTitle.size > 1) {
            val byArtist = sameTitle.filter { sameArtist(it.author, entry.artist) }
            return if (byArtist.size == 1) QrMatch.Found(byArtist[0]) else QrMatch.Similar(sameTitle.take(MAX_CANDIDATES))
        }
        val similar = library
            .map { it to similarity(TextKey.of(it.title), kt) }
            .filter { it.second >= SIMILAR_MIN }
            .sortedByDescending { it.second }
            .take(MAX_CANDIDATES)
            .map { it.first }
        return if (similar.isEmpty()) QrMatch.Missing else QrMatch.Similar(similar)
    }

    // a mesma regra de "mesma música" da biblioteca (SongMatch)
    fun sameArtist(a: String, b: String): Boolean = SongMatch.sameArtistKey(TextKey.of(a), TextKey.of(b))

    /** 1 = iguais; pela distância de edição (letras trocadas, faltando ou sobrando). */
    fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return 1.0 - prev[b.length].toDouble() / maxOf(a.length, b.length)
    }
}
