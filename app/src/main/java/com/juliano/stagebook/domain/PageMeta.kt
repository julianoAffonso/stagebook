package com.juliano.stagebook.domain

import kotlinx.serialization.Serializable

/** O que o script de extração lê da página aberta no navegador de importação. */
@Serializable
data class PageData(
    val title: String = "",
    val artist: String = "",
    val text: String = "",
    val chordText: String = "",   // cifra com os acordes em cima (Cifra Club)
    val key: String = "",
    val capo: String = "",
    val bpm: String = "",   // "120 BPM" / "BPM: 120" / "Tempo: 120" escrito na página, se houver
    val site: String = "",
    val ldName: String = "",
    val ldArtist: String = "",
    val ldDate: String = "",
    val ogTitle: String = "",
    val docTitle: String = "",
)

/**
 * Título e artista: dados estruturados da página > og:title / título da aba ("Música - Artista - SITE")
 * > h1/h2. Descarta o que for o nome do próprio site.
 */
object PageMeta {
    data class TitleArtist(val title: String, val artist: String)

    private val siteNames = Regex(".*(letrasmus|letras|vagalume|cifraclub|cifras|genius|azlyrics|musixmatch|lyrics|google).*")
    private val separator = Regex("\\s[-–|]\\s")
    private val suffix = Regex("(?i)\\s*[-|–]\\s*(letra|lyrics|cifra).*$")
    private val prefix = Regex("(?i)^letra( de| da)?\\s+")
    private val translationTag = Regex("(?i)\\s*\\((tradução|traducao|translation|traducción)\\)")

    fun pick(p: PageData): TitleArtist {
        val og = splitTitle(p.ogTitle)
        val doc = splitTitle(p.docTitle)
        val title = first(p.site, p.ldName, og.title, doc.title, p.title)
        var artist = first(p.site, p.ldArtist, og.artist, doc.artist, p.artist)
        if (TextKey.of(artist) == TextKey.of(title)) artist = ""
        return TitleArtist(title, artist)
    }

    fun yearOf(p: PageData): String = p.ldDate.take(4).takeIf { it.length == 4 && it.all(Char::isDigit) }.orEmpty()

    private fun first(site: String, vararg candidates: String): String =
        candidates.map { it.trim() }.firstOrNull { it.isNotEmpty() && it.length <= 80 && !isSite(it, site) }.orEmpty()

    /** "LETRAS.MUS.BR", "Vagalume", "Cifra Club", "Genius"… não são título nem artista. */
    fun isSite(s: String, host: String): Boolean {
        val k = TextKey.of(s).replace(" ", "")
        if (k.isEmpty()) return true
        val h = TextKey.of(host.removePrefix("www.")).replace(" ", "")
        return (h.isNotEmpty() && (h.startsWith(k) || k.contains(h))) || siteNames.matches(k)
    }

    /** "Africa - Toto - LETRAS.MUS.BR" → Africa / Toto */
    fun splitTitle(raw: String): TitleArtist {
        var title = raw.trim()
        var artist = ""
        if (separator.containsMatchIn(title)) {
            val parts = title.split(separator)
            title = parts[0].trim()
            if (parts.size > 1) artist = parts[1].trim()
        }
        title = title.replace(translationTag, "").replace(suffix, "").replace(prefix, "").trim()
        return TitleArtist(title, artist)
    }
}
