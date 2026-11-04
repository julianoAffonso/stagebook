package br.app.stagebook

import br.app.stagebook.ui.setlists.SetlistRowUi
import br.app.stagebook.ui.setlists.setlistOrder
import br.app.stagebook.ui.setlists.setlistSearch
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Setlists tab in newest first or A to Z (user, 03/11). */
class SetlistOrderTest {
    private val sets = listOf("sábado", "Ensaio", "abertura", "Bar do Zé").mapIndexed { i, n -> SetlistRowUi("$i", n, 0) }

    @Test fun `newest keeps the stored order`() = assertEquals(sets, setlistOrder(sets, abc = false))

    @Test fun `a to z ignores case and accents`() =
        assertEquals(listOf("abertura", "Bar do Zé", "Ensaio", "sábado"), setlistOrder(sets, abc = true).map { it.name })

    @Test fun `search ignores case and accents`() =
        assertEquals(listOf("sábado"), setlistSearch(sets, "SABADO").map { it.name })

    @Test fun `search keeps the order it was given`() =
        assertEquals(listOf("Bar do Zé", "sábado"), setlistSearch(setlistOrder(sets, abc = true), "ba").map { it.name })

    @Test fun `a blank search shows everything`() = assertEquals(sets, setlistSearch(sets, "  "))
}
