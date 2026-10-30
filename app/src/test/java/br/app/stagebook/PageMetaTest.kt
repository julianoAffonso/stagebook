package br.app.stagebook

import br.app.stagebook.domain.PageData
import br.app.stagebook.domain.PageMeta
import org.junit.Assert.assertEquals
import org.junit.Test

class PageMetaTest {
    @Test
    fun `letras mus br on the translation page`() {
        // valores lidos do site em 18/10/2026 (Chrome)
        val p = PageData(
            title = "Hazard", artist = "LETRAS.MUS.BR - Letras de músicas", site = "www.letras.mus.br",
            ldName = "Hazard", ldArtist = "Richard Marx",
            ogTitle = "HAZARD (TRADUÇÃO) - Richard Marx - LETRAS.MUS.BR", docTitle = "HAZARD (TRADUÇÃO) - Richard Marx - LETRAS.MUS.BR",
        )
        assertEquals(PageMeta.TitleArtist("Hazard", "Richard Marx"), PageMeta.pick(p))
    }

    @Test
    fun `without structured data uses the tab title and drops the site name`() {
        val p = PageData(
            title = "LETRAS.MUS.BR", artist = "LETRAS.MUS.BR - Letras de músicas", site = "www.letras.mus.br",
            docTitle = "Malandragem - Cássia Eller - LETRAS.MUS.BR",
        )
        assertEquals(PageMeta.TitleArtist("Malandragem", "Cássia Eller"), PageMeta.pick(p))
    }

    @Test
    fun `removes lyrics and translation prefix and suffix`() {
        assertEquals("Africa", PageMeta.splitTitle("Letra de Africa").title)
        assertEquals("Hazard", PageMeta.splitTitle("Hazard (Tradução)").title)
        assertEquals("Toto", PageMeta.splitTitle("Africa - Toto - Lyrics").artist)
    }

    @Test
    fun `year from structured data`() {
        assertEquals("1991", PageMeta.yearOf(PageData(ldDate = "1991-05-01")))
        assertEquals("", PageMeta.yearOf(PageData(ldDate = "s/d")))
    }
}
