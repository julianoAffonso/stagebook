package br.app.stagebook.domain

/**
 * Quando duas músicas são a mesma: mesmo título (sem acento/caixa/pontuação) e mesmo artista.
 * Artista em branco de um dos lados vale como "qualquer um". Assim "It's My Life" do Bon Jovi e
 * do Talk Talk convivem, e importar de novo atualiza em vez de duplicar.
 */
object SongMatch {
    fun same(titleA: String, artistA: String, titleB: String, artistB: String): Boolean =
        TextKey.of(titleA) == TextKey.of(titleB) && sameArtistKey(TextKey.of(artistA), TextKey.of(artistB))

    /** Artistas já como chave ([TextKey.of]): para quem compara muitas músicas sem refazer as chaves. */
    fun sameArtistKey(a: String, b: String): Boolean = a.isEmpty() || b.isEmpty() || a == b || a.contains(b) || b.contains(a)
}
