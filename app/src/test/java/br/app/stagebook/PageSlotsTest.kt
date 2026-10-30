package br.app.stagebook

import br.app.stagebook.domain.PageSlots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bolinhas do rodapé do palco: todas quando cabem; senão primeira, atual com vizinhas, "…" e última. */
class PageSlotsTest {
    // em números de página (1 = primeira), "…" no lugar dos null, para ler como na tela
    private fun shown(current: Int, count: Int, capacity: Int) =
        PageSlots.of(current - 1, count, capacity).joinToString(" ") { it?.let { i -> "${i + 1}" } ?: "…" }

    @Test fun `when it fits, shows all`() {
        assertEquals("1 2 3 4", shown(2, 4, 7))
        assertEquals("1 2 3 4 5 6 7", shown(7, 7, 7))
    }

    @Test fun `at the start, a solid block, ellipsis and the last one`() {
        assertEquals("1 2 3 4 5 … 12", shown(1, 12, 7))
        assertEquals("1 2 3 4 5 … 12", shown(4, 12, 7))
    }

    @Test fun `in the middle, the current one with its neighbors between ellipses`() {
        assertEquals("1 … 4 5 6 … 12", shown(5, 12, 7))
        assertEquals("1 … 6 7 8 … 12", shown(7, 12, 7))
    }

    @Test fun `at the end, the first one, ellipsis and a solid block`() {
        assertEquals("1 … 8 9 10 11 12", shown(9, 12, 7))
        assertEquals("1 … 8 9 10 11 12", shown(12, 12, 7))
    }

    @Test fun `never goes over what fits and always shows the current one`() {
        for (capacity in 1..12) for (count in 1..30) for (current in 0 until count) {
            val slots = PageSlots.of(current, count, capacity)
            assertTrue("$current/$count em $capacity", slots.size <= maxOf(capacity, 1))
            assertTrue("$current/$count em $capacity", current in slots)
            assertEquals("sem repetir", slots.filterNotNull().size, slots.filterNotNull().toSet().size)
        }
    }
}
