package br.app.stagebook

import br.app.stagebook.domain.PageData
import br.app.stagebook.domain.PageMeta
import org.junit.Assert.assertEquals
import org.junit.Test

class PageMetaTest {
    @Test
    fun `letras mus br on the translation page`() {
        // values read from the site on 18/10/2026 (Chrome)
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

    @Test fun `real titles and artists aren't taken for sites`() {
        val cifra = "www.cifraclub.com.br"
        assertEquals("Genius of Love", PageMeta.pick(PageData(ldName = "Genius of Love", ldArtist = "Banda", site = cifra)).title)
        assertEquals("Vagalume", PageMeta.pick(PageData(ldName = "Vagalume", ldArtist = "Banda", site = cifra)).title)
        assertEquals("Letras Perdidas", PageMeta.pick(PageData(ldName = "Letras Perdidas", ldArtist = "Banda", site = cifra)).title)
        assertEquals("Lyrics Born", PageMeta.pick(PageData(ldName = "Musica", ldArtist = "Lyrics Born", site = cifra)).artist)
        assertEquals("Let", PageMeta.pick(PageData(ldName = "Let", ldArtist = "Banda", site = "www.letras.mus.br")).title)
    }

    @Test fun `the page's own site is still left out`() {
        assertEquals("Africa", PageMeta.pick(PageData(ogTitle = "Africa - Toto - LETRAS.MUS.BR", site = "www.letras.mus.br")).title)
        assertEquals(true, PageMeta.isSite("Cifra Club", "www.cifraclub.com.br"))
        assertEquals(true, PageMeta.isSite("Vagalume", ""))
    }
}
