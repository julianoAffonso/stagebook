package com.juliano.stagebook

import com.juliano.stagebook.domain.PageSlots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bolinhas do rodapé do palco: todas quando cabem; senão primeira, atual com vizinhas, "…" e última. */
class PageSlotsTest {
    // em números de página (1 = primeira), "…" no lugar dos null, para ler como na tela
    private fun shown(current: Int, count: Int, capacity: Int) =
        PageSlots.of(current - 1, count, capacity).joinToString(" ") { it?.let { i -> "${i + 1}" } ?: "…" }

    @Test fun `cabendo, mostra todas`() {
        assertEquals("1 2 3 4", shown(2, 4, 7))
        assertEquals("1 2 3 4 5 6 7", shown(7, 7, 7))
    }

    @Test fun `no começo, bloco corrido, reticências e a última`() {
        assertEquals("1 2 3 4 5 … 12", shown(1, 12, 7))
        assertEquals("1 2 3 4 5 … 12", shown(4, 12, 7))
    }

    @Test fun `no meio, a atual com as vizinhas entre reticências`() {
        assertEquals("1 … 4 5 6 … 12", shown(5, 12, 7))
        assertEquals("1 … 6 7 8 … 12", shown(7, 12, 7))
    }

    @Test fun `no fim, a primeira, reticências e bloco corrido`() {
        assertEquals("1 … 8 9 10 11 12", shown(9, 12, 7))
        assertEquals("1 … 8 9 10 11 12", shown(12, 12, 7))
    }

    @Test fun `nunca passa do que cabe e sempre mostra a atual`() {
        for (capacity in 1..12) for (count in 1..30) for (current in 0 until count) {
            val slots = PageSlots.of(current, count, capacity)
            assertTrue("$current/$count em $capacity", slots.size <= maxOf(capacity, 1))
            assertTrue("$current/$count em $capacity", current in slots)
            assertEquals("sem repetir", slots.filterNotNull().size, slots.filterNotNull().toSet().size)
        }
    }
}
