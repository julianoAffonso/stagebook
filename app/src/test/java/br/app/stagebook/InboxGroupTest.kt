package br.app.stagebook

import br.app.stagebook.data.BandInbox
import br.app.stagebook.data.groupInbox
import org.junit.Assert.assertEquals
import org.junit.Test

/** Several members sending a setlist (T11): the same setlist stays together, newest first; nothing goes missing. */
class InboxGroupTest {
    private fun item(id: String, from: String, set: String, at: Long) = BandInbox.Item(id, from, from, "STAGEBOOK1\n1\n$set\nMúsica", at = at)

    @Test fun `same setlist from different people stays together - newest first`() {
        val groups = groupInbox(listOf(item("1", "Ana", "Show", 100), item("2", "Beto", "Domingo", 150), item("3", "Caio", "show", 200)))
        assertEquals(listOf(listOf("3", "1"), listOf("2")), groups.map { g -> g.map { it.id } })
    }

    @Test fun `loose songs each stay on their own`() {
        val loose = listOf(BandInbox.Item("a", "x", "Ana", "STAGEBOOK1\n0\n", at = 1), BandInbox.Item("b", "y", "Beto", "STAGEBOOK1\n0\n", at = 2))
        assertEquals(2, groupInbox(loose).size)
    }
}
