package br.app.stagebook

import br.app.stagebook.domain.LineDiff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** One line diff for the lyrics view and the merge, bounded (deep audit, 30/10). */
class LineDiffTest {
    @Test fun `equal lines in order, with the changed stretch in the middle`() {
        val a = listOf("um", "dois", "três", "quatro")
        val b = listOf("um", "dois e meio", "três", "quatro")
        assertEquals(listOf(0 to 0, 2 to 2, 3 to 3), LineDiff.pairs(a, b))
    }

    @Test fun `a huge changed text doesn't run out of memory`() {
        val a = List(30_000) { "a" }
        val b = List(30_000) { "b" }
        val started = System.nanoTime()
        assertEquals(emptyList<Pair<Int, Int>>(), LineDiff.pairs(a, b))   // too big for the table: counts as replaced
        assertTrue(System.nanoTime() - started < 2_000_000_000L)
    }

    @Test fun `one keystroke in a long song compares only the changed line`() {
        val song = List(3_000) { "linha $it" }
        val typed = song.toMutableList().also { it[1500] = "linha 1500x" }
        assertEquals(2_999, LineDiff.pairs(song, typed).size)
    }
}
