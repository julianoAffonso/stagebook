package br.app.stagebook

import br.app.stagebook.domain.SongMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Finding "the same song" among many gives the same answer as comparing one by one, much faster (audit 30/10). */
class SongIndexTest {
    private data class S(val title: String, val artist: String)

    @Test fun `same rules as SongMatch_same, first match wins`() {
        val list = listOf(S("Tempo Perdido", "Legião Urbana"), S("Tempo perdido", "Outra"), S("Hazard", ""))
        val index = SongMatch.Index(list, { it.title }, { it.artist })
        assertEquals(list[0], index.find("tempo perdido", "legiao urbana"))
        assertEquals(list[1], index.find("Tempo Perdido", "Outra"))
        assertEquals(list[0], index.find("Tempo Perdido", ""))   // no artist: the first with that title
        assertEquals(list[2], index.find("Hazard", "Richard Marx"))
        assertNull(index.find("Noite clara", ""))
    }

    @Test fun `matches what the one-by-one search finds on a big library`() {
        val library = (1..2000).map { S("Música $it", "Banda ${it % 7}") }
        val incoming = (1500..2500).map { S("música $it", "banda ${it % 7}") }
        val index = SongMatch.Index(library, { it.title }, { it.artist })
        val fast = incoming.map { index.find(it.title, it.artist) }
        val slow = incoming.map { i -> library.firstOrNull { SongMatch.same(it.title, it.artist, i.title, i.artist) } }
        assertEquals(slow, fast)
    }
}
