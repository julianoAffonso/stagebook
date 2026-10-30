package br.app.stagebook

import br.app.stagebook.domain.SongMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Different versions of the same song: accepting theirs never deletes the receiver's chords. */
class SongMergeTest {
    private val mine = "{key: G}\n[G]Linha um da can[D]ção\n[Em]Linha dois\nLinha três"

    @Test fun `new lyrics without chords come in with my chords`() {
        val incoming = "Linha um da canção\nLinha dois corrigida\nLinha três"
        val merged = SongMerge.accept(mine, incoming)
        assertTrue(merged.contains("{key: G}"))
        assertTrue(merged.contains("[G]Linha um da can[D]ção"))
        assertTrue(merged.contains("Linha dois corrigida"))
    }

    @Test fun `received with chords comes in whole`() {
        val incoming = "[A]Linha um da canção\nLinha dois"
        assertEquals(incoming, SongMerge.accept(mine, incoming))
    }

    @Test fun `shows only the lines that change - and nothing when only the chords change`() {
        val changes = SongMerge.changes(mine, "Linha um da canção\nLinha dois corrigida\nLinha três")
        assertEquals(listOf(SongMerge.Change(SongMerge.Kind.REMOVED, "Linha dois"), SongMerge.Change(SongMerge.Kind.ADDED, "Linha dois corrigida")), changes)
        assertTrue(SongMerge.changes(mine, "[C]Linha um da canção\nLinha dois\nLinha três").isEmpty())
    }

    @Test fun `trailing space and trailing blank line do not count as a difference`() {
        assertTrue(SongMerge.same("a\nb", "a  \nb\n\n"))
        assertFalse(SongMerge.same("a\nb", "a\nc"))
    }
}
