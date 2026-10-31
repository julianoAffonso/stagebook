package br.app.stagebook

import br.app.stagebook.domain.ChordImport
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sites pad chord lines with non-breaking and thin spaces; they're still chord lines (user, 31/10: only lyrics came). */
class ChordSpacesTest {
    private val nb = " "
    private val page = "G${nb}${nb}${nb}${nb}D\numa linha de letra aqui\nEm${nb}${nb}${nb}C\noutra linha de letra \nG  D\nmais uma linha"

    @Test fun `chord lines padded with non-breaking spaces are chords`() = assertTrue(ChordImport.hasChords(page))

    @Test fun `they become ChordPro`() = assertTrue(ChordImport.toChordPro(page).contains("[D]"))
}
