package br.app.stagebook

import br.app.stagebook.domain.SongGroups
import br.app.stagebook.domain.TextKey
import org.junit.Assert.assertEquals
import org.junit.Test

/** The A to Z groups of the song list: which letter a title is in, and when a list is long enough to have them. */
class SongGroupsTest {
    @Test fun `the group is the first letter, upper case and without the accent`() {
        assertEquals("A", SongGroups.letter("À Francesa"))
        assertEquals("E", SongGroups.letter("Élan"))
        assertEquals("C", SongGroups.letter("çapo"))
        assertEquals("M", SongGroups.letter("  malha fina"))
        assertEquals("Z", SongGroups.letter("Zíper"))
    }

    @Test fun `a digit, a symbol or no title goes to the other group`() {
        assertEquals("#", SongGroups.letter("9 Luas"))
        assertEquals("#", SongGroups.letter("(Quase) Nada"))
        assertEquals("#", SongGroups.letter("...E Depois"))
        assertEquals("#", SongGroups.letter("¿Dónde?"))
        assertEquals("#", SongGroups.letter(""))
        assertEquals("#", SongGroups.letter("   "))
    }

    private fun group(titles: List<String>) = SongGroups.group(TextKey.sorted(titles) { it }, SongGroups::letter)

    @Test fun `the other group comes first, then the letters in order, each where its first song is`() {
        val titles = listOf("{Chave}", "Zebra", "Élan", "Eco", "9 Luas", "(Quase) Nada", "Alba", "À Francesa", "Brisa") + (1..12).map { "Canção $it" }
        val grouped = group(titles)
        assertEquals(listOf("#", "A", "B", "C", "E", "Z"), grouped.groups.map { it.letter })
        assertEquals(listOf(0, 3, 5, 6, 18, 20), grouped.groups.map { it.first })
        // every song of a group sits between its header and the next one
        grouped.groups.forEachIndexed { g, group ->
            val end = grouped.groups.getOrNull(g + 1)?.first ?: grouped.rows.size
            assertEquals(List(end - group.first) { group.letter }, grouped.rows.subList(group.first, end).map(SongGroups::letter))
        }
        assertEquals(titles.sorted(), grouped.rows.sorted())
        // inside a group the title order stays
        assertEquals(listOf("À Francesa", "Alba"), grouped.rows.subList(3, 5))
    }

    @Test fun `under twenty songs there are no groups, and the list stays as it came`() {
        val titles = ('A'..'S').map { "$it canção" }   // 19 songs, 19 letters
        val grouped = group(titles)
        assertEquals(emptyList<SongGroups.Group>(), grouped.groups)
        assertEquals(TextKey.sorted(titles) { it }, grouped.rows)
        assertEquals(20, group(titles + "T canção").groups.size)
    }

    @Test fun `under three groups there are none, however many songs`() {
        val two = (1..15).map { "Alba $it" } + (1..15).map { "Brisa $it" }
        assertEquals(emptyList<SongGroups.Group>(), group(two).groups)
        assertEquals(listOf("A", "B", "C"), group(two + "Cais").groups.map { it.letter })
    }
}
