package br.app.stagebook

import br.app.stagebook.ui.setlists.SetlistRowUi
import br.app.stagebook.ui.setlists.setlistOrder
import br.app.stagebook.ui.setlists.setlistSearch
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Setlists tab has one order, newest first, and no A to Z (user, 19/11). */
class SetlistOrderTest {
    private val sets = listOf("sábado", "Ensaio", "abertura", "Bar do Zé").mapIndexed { i, n -> SetlistRowUi("$i", n, 0) }

    @Test fun `with no dates the stored order stays`() = assertEquals(sets, setlistOrder(sets))

    @Test fun `search ignores case and accents`() =
        assertEquals(listOf("sábado"), setlistSearch(sets, "SABADO").map { it.name })

    @Test fun `search keeps the order it was given`() =
        assertEquals(listOf("sábado", "Bar do Zé"), setlistSearch(setlistOrder(sets), "ba").map { it.name })

    @Test fun `a blank search shows everything`() = assertEquals(sets, setlistSearch(sets, "  "))
}
