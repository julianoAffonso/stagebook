package br.app.stagebook

import br.app.stagebook.domain.SetlistBreaks
import br.app.stagebook.domain.SetlistBreaks.BREAK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Breaks inside a setlist: always between two songs, whatever is moved, added or taken out. */
class SetlistBreaksTest {
    @Test fun `only breaks between two songs stay, sorted and one per place`() {
        assertEquals(listOf(1, 3), SetlistBreaks.normalise(listOf(3, 0, 1, 3, 4, -2, 9), 4))
        assertEquals(emptyList<Int>(), SetlistBreaks.normalise(listOf(1), 1))
        assertEquals(emptyList<Int>(), SetlistBreaks.normalise(listOf(0, 1), 0))
    }

    @Test fun `songs and breaks as rows, and back`() {
        val rows = SetlistBreaks.rows(5, listOf(3, 1))
        assertEquals(listOf(0, BREAK, 1, 2, BREAK, 3, 4), rows)
        assertEquals(SetlistBreaks.Moved(listOf(0, 1, 2, 3, 4), listOf(1, 3)), SetlistBreaks.split(rows))
        assertEquals(listOf(0, 1), SetlistBreaks.rows(2, emptyList()))
    }

    @Test fun `a break left at the top or the bottom goes back between two songs`() {
        assertEquals(listOf(1), SetlistBreaks.split(listOf(BREAK, 0, 1, 2)).breaks)
        assertEquals(listOf(2), SetlistBreaks.split(listOf(0, 1, 2, BREAK)).breaks)
        // two together are one
        assertEquals(listOf(1), SetlistBreaks.split(listOf(0, BREAK, BREAK, 1, 2)).breaks)
        // nowhere to be with a single song
        assertEquals(emptyList<Int>(), SetlistBreaks.split(listOf(BREAK, 0)).breaks)
    }

    @Test fun `dragging a break moves only the break`() {
        // A B | C D  →  the break (row 2) dropped on row 1  →  A | B C D
        assertEquals(SetlistBreaks.Moved(listOf(0, 1, 2, 3), listOf(1)), SetlistBreaks.moveRow(4, listOf(2), 2, 1))
        // to the very top: it stays after the first song
        assertEquals(listOf(1), SetlistBreaks.moveRow(4, listOf(2), 2, 0).breaks)
        // to the very bottom: before the last
        assertEquals(listOf(3), SetlistBreaks.moveRow(4, listOf(2), 2, 4).breaks)
    }

    @Test fun `dragging a song across a break changes its block`() {
        // A B | C D  →  D (row 4) dropped on row 1  →  A D B | C
        assertEquals(SetlistBreaks.Moved(listOf(0, 3, 1, 2), listOf(3)), SetlistBreaks.moveRow(4, listOf(2), 4, 1))
        // A B | C D  →  A (row 0) dropped on row 2, just past the break  →  B | A C D
        assertEquals(SetlistBreaks.Moved(listOf(1, 0, 2, 3), listOf(1)), SetlistBreaks.moveRow(4, listOf(2), 0, 2))
        // out of range: nothing moves
        assertEquals(SetlistBreaks.Moved(listOf(0, 1, 2, 3), listOf(2)), SetlistBreaks.moveRow(4, listOf(2), 9, 0))
    }

    @Test fun `moving a song by its place among the songs keeps the break between its neighbours`() {
        // A B | C D  →  A to the place of D  →  B | C D A
        assertEquals(SetlistBreaks.Moved(listOf(1, 2, 3, 0), listOf(1)), SetlistBreaks.moveSong(4, listOf(2), 0, 3))
        // A B | C D  →  D to the place of A  →  D A B | C
        assertEquals(SetlistBreaks.Moved(listOf(3, 0, 1, 2), listOf(3)), SetlistBreaks.moveSong(4, listOf(2), 3, 0))
        assertEquals(SetlistBreaks.Moved(listOf(0, 1, 2, 3), listOf(2)), SetlistBreaks.moveSong(4, listOf(2), 0, 4))
    }

    @Test fun `a new break goes before the last song, and needs two songs`() {
        assertEquals(listOf(3), SetlistBreaks.add(emptyList(), 4))
        // that place taken: the one before it
        assertEquals(listOf(2, 3), SetlistBreaks.add(listOf(3), 4))
        assertEquals(emptyList<Int>(), SetlistBreaks.add(emptyList(), 1))
        assertTrue(SetlistBreaks.canAdd(emptyList(), 2))
        assertFalse(SetlistBreaks.canAdd(emptyList(), 1))
        assertFalse(SetlistBreaks.canAdd(listOf(1), 2))   // every place has one
    }

    @Test fun `removing a break leaves the others`() {
        assertEquals(listOf(1), SetlistBreaks.remove(listOf(1, 3), 3))
        assertEquals(listOf(1, 3), SetlistBreaks.remove(listOf(1, 3), 2))
    }

    @Test fun `taking songs out keeps each break before the same song`() {
        // A | B C | D E, without B: A | C | D E
        assertEquals(listOf(1, 2), SetlistBreaks.afterRemoving(listOf(1, 3), 5, setOf(1)))
        // without A: the first break would be at the top, so it goes
        assertEquals(listOf(2), SetlistBreaks.afterRemoving(listOf(1, 3), 5, setOf(0)))
        // without D and E: the last break would be at the bottom
        assertEquals(listOf(1), SetlistBreaks.afterRemoving(listOf(1, 3), 5, setOf(3, 4)))
        // without B and C: the two breaks meet and become one
        assertEquals(listOf(1), SetlistBreaks.afterRemoving(listOf(1, 3), 5, setOf(1, 2)))
    }

    @Test fun `replacing the songs keeps each break before the same song when it is still there`() {
        val old = listOf("a", "b", "c", "d")
        // a b | c d reordered to d c b a: the break is still before c
        assertEquals(listOf(1), SetlistBreaks.afterReplacing(listOf(2), old, listOf("d", "c", "b", "a")))
        // c is gone: before the next one that stayed
        assertEquals(listOf(2), SetlistBreaks.afterReplacing(listOf(2), old, listOf("a", "b", "d")))
        // nothing after it stayed, or it would be at the top: no break
        assertEquals(emptyList<Int>(), SetlistBreaks.afterReplacing(listOf(2), old, listOf("a", "b")))
        assertEquals(emptyList<Int>(), SetlistBreaks.afterReplacing(listOf(2), old, listOf("c", "a")))
        // songs added at the end change nothing
        assertEquals(listOf(2), SetlistBreaks.afterReplacing(listOf(2), old, old + "e"))
    }

    @Test fun `blocks are the songs between the breaks`() {
        assertEquals(listOf(5), SetlistBreaks.blocks(5, emptyList()))
        assertEquals(listOf(3, 2), SetlistBreaks.blocks(5, listOf(3)))
        assertEquals(listOf(1, 2, 2), SetlistBreaks.blocks(5, listOf(1, 3)))
    }

    @Test fun `the break's counter shows minutes and seconds`() {
        assertEquals("00:00", SetlistBreaks.clock(0))
        assertEquals("00:07", SetlistBreaks.clock(7_999))
        assertEquals("12:30", SetlistBreaks.clock(750_000))
        assertEquals("75:03", SetlistBreaks.clock(4_503_000))
        assertEquals("00:00", SetlistBreaks.clock(-5_000))   // the device's clock was set back
    }
}
