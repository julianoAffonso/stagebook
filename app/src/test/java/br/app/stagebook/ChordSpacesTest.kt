package br.app.stagebook

import br.app.stagebook.domain.ChordImport
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sites pad chord lines with non-breaking and thin spaces; they're still chord lines (user, 31/10: only lyrics came). */
class ChordSpacesTest {
    private val nb = " "
    private val page = "G${nb}${nb}${nb}${nb}D\numa linha de letra aqui\nEm${nb}${nb}${nb}C\noutra linha de letra \nG  D\nmais uma linha"

    @Test fun `chord lines padded with non-breaking spaces are chords`() = assertTrue(ChordImport.hasChords(page))

    // Cifra Club, Fullgás (31/10): a zero-width space at the start of every line
    @Test fun `lines starting with an invisible mark are chords too`() {
        val z = "\u200B"
        val zw = "${z}[Intro]\n${z}Bm  G\n${z}uma linha de letra\n${z}A   D\n${z}outra linha"
        assertTrue(ChordImport.hasChords(zw))
        assertTrue(ChordImport.toChordPro(zw).contains("[Bm]"))
    }

    @Test fun `title with the artist in front loses the artist`() {
        val m = br.app.stagebook.domain.PageMeta.pick(br.app.stagebook.domain.PageData(ldName = "Marina Lima - Fullgás", ldArtist = "Marina Lima", site = "www.cifraclub.com.br"))
        org.junit.Assert.assertEquals("Fullgás", m.title)
        org.junit.Assert.assertEquals("Marina Lima", m.artist)
    }

    @Test fun `they become ChordPro`() = assertTrue(ChordImport.toChordPro(page).contains("[D]"))
}
