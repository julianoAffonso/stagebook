package br.app.stagebook

import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.LyricsLanguage
import br.app.stagebook.domain.Tags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Song tags (vocalist, 01/11): free names, filter with "and", and splitting Brazilian from international. */
class TagsTest {
    private val songs = listOf(
        Song("1", "Tempo Perdido", tags = listOf("Nacional", "Rock")),
        Song("2", "Hazard", tags = listOf("Internacional")),
        Song("3", "Malandragem", tags = listOf("nacional")),
    )

    @Test fun `every tag once, the same name in another case is the same tag`() =
        assertEquals(listOf("Internacional", "Nacional", "Rock"), Tags.all(songs))

    @Test fun `no tag chosen shows everything`() = assertEquals(3, Tags.filter(songs, emptySet()) { it.tags }.size)

    @Test fun `one tag, ignoring case`() =
        assertEquals(listOf("1", "3"), Tags.filter(songs, setOf("Nacional")) { it.tags }.map { it.id })

    // "or" would confuse (user, 01/11)
    @Test fun `several tags show only the songs with all of them`() =
        assertEquals(listOf("1"), Tags.filter(songs, setOf("Nacional", "Rock")) { it.tags }.map { it.id })

    @Test fun `adding a tag already there in another case does not repeat it`() {
        assertEquals(listOf("Nacional"), Tags.add(listOf("Nacional"), "nacional"))
        assertEquals(emptyList<String>(), Tags.remove(listOf("Nacional"), "NACIONAL"))
        assertEquals("Rock nacional", Tags.clean("  Rock   nacional "))
    }

    @Test fun `Portuguese lyrics with chords and sections`() =
        assertEquals("pt", LyricsLanguage.detect("[Refrão]\n[G]Eu quero você [C]comigo\nQue a vida é tão bonita quando você está aqui"))

    @Test fun `English lyrics`() =
        assertEquals("en", LyricsLanguage.detect("{key: E}\n[E]You know I love you, baby\nAnd I can't let it go, it's all in my heart"))

    @Test fun `too little lyrics to tell`() {
        assertNull(LyricsLanguage.detect("[G] [D] [Em] [C]"))
        assertNull(LyricsLanguage.detect(""))
    }

    @Test fun `tags survive saving the library`() {
        val lib = br.app.stagebook.data.model.Library(songs = songs)
        val back = br.app.stagebook.data.LibraryCodec.decode(br.app.stagebook.data.LibraryCodec.encode(lib)) { "x" }
        assertEquals(songs.map { it.tags }, back.songs.map { it.tags })
        assertTrue(Tags.has(back.songs[0], "rock"))
        assertFalse(Tags.has(back.songs[1], "rock"))
    }

    // "national" is the app's language (user, 01/11: not only for Brazil)
    @Test fun `a tie or an unknown mix gives no language`() {
        assertNull(LyricsLanguage.detect("eu you que the"))   // 2 x 2, too few words anyway
        assertTrue("pt" in LyricsLanguage.known && "en" in LyricsLanguage.known)
    }

}
