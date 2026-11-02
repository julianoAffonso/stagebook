package br.app.stagebook

import br.app.stagebook.data.LibraryCodec
import br.app.stagebook.data.MusicBrainz
import br.app.stagebook.data.mergeSongs
import br.app.stagebook.data.model.Library
import br.app.stagebook.data.model.Look
import br.app.stagebook.data.model.SectionLang
import br.app.stagebook.data.model.Setlist
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.PedalAction
import br.app.stagebook.domain.QrPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {
    private var n = 0
    private val newId = { "id${++n}" }

    @Test
    fun `opens the letras json from the Java version`() {
        // formato gravado pelo app antigo (org.json, toString(1))
        val old = """
            {"version": 1,
             "songs": [{"id": "a", "title": "Hazard", "author": "Richard Marx", "year": "1991", "text": "linha"}],
             "sets": [{"id": "s", "name": "Show", "items": ["a"]}],
             "look": {"fontSp": 34, "fg": -1, "bg": -16777216, "bold": true, "sectionLang": "en",
                      "sectionColor": -12409355, "showSections": false}}
        """.trimIndent()
        val lib = LibraryCodec.decode(old, newId)
        assertEquals("Hazard", lib.songs.single().title)
        assertEquals(listOf("a"), lib.sets.single().items)
        assertEquals(34f, lib.look.fontSp)
        assertEquals(SectionLang.EN, lib.look.sectionLang)
        assertEquals(false, lib.look.showSections)
        assertEquals("biblioteca antiga sem a opção: tela ligada por padrão", true, lib.look.keepScreenOn)
        assertEquals(br.app.stagebook.data.model.StageColumns.AUTO, lib.look.columns)
        assertEquals(lib, LibraryCodec.decode(LibraryCodec.encode(lib), newId))
    }

    @Test
    fun `unknown value falls back to the default instead of losing the library`() {
        val lib = LibraryCodec.decode("""{"songs":[{"title":"X"}],"look":{"sectionLang":"fr"}}""", newId)
        assertEquals(SectionLang.PT, lib.look.sectionLang)
        assertEquals("id1", lib.songs.single().id)
    }

    @Test
    fun `import replaces by title and adds the new ones`() {
        val lib = Library(songs = listOf(Song("a", "Tempo Perdido", text = "velha")))
        val incoming = LibraryCodec.decodeSongs("""{"songs":[{"title":"tempo perdido","text":"nova"},{"title":"Hazard"},{"title":" "}]}""")
        val (merged, result) = lib.mergeSongs(incoming, newId)
        assertEquals(1, result.added)
        assertEquals(1, result.replaced)
        assertEquals("a", merged.songs[0].id)
        assertEquals("nova", merged.songs[0].text)
        assertEquals("Hazard", merged.songs[1].title)
    }

    @Test
    fun `import does not duplicate and keeps same-title songs by different artists`() {
        val lib = Library(songs = listOf(Song("a", "It's My Life", "Bon Jovi", text = "velha", zoom = 1.4f)))
        val incoming = listOf(
            Song(title = "Its my life", author = "bon jovi", text = "nova"),     // the same one: updates
            Song(title = "It's My Life", author = "Talk Talk", text = "outra"),  // another song
            Song(title = "It's My Life", author = "Talk Talk", text = "outra 2"), // repeated in the file: updates
        )
        val (merged, result) = lib.mergeSongs(incoming, newId)
        assertEquals(1, result.added)
        assertEquals(2, result.replaced)
        assertEquals(2, merged.songs.size)
        assertEquals("nova", merged.songs[0].text)
        assertEquals("o zoom do palco continua", 1.4f, merged.songs[0].zoom)
        assertEquals("outra 2", merged.songs[1].text)
        // importing the same file again doesn't change the count
        assertEquals(2, merged.mergeSongs(incoming, newId).first.songs.size)
    }

    private fun unpack(bytes: ByteArray): String {
        assertEquals('Q'.code.toByte(), bytes[0]); assertEquals('1'.code.toByte(), bytes[1])
        val inf = java.util.zip.Inflater(true)
        inf.setInput(bytes, 2, bytes.size - 2)
        val out = ByteArray(65536); val n = inf.inflate(out); inf.end()
        return String(out, 0, n, Charsets.UTF_8)
    }

    @Test
    fun `QR carries the count and the artist only on repeated titles, compressed`() {
        val lib = Library(songs = listOf(
            Song("a", "Hazard", "Richard Marx"), Song("b", "Malandragem"),
            Song("c", "It's My Life", "Bon Jovi"), Song("d", "Its my life", "Talk Talk"),
        ))
        val set = Setlist("s", "Show de sábado", listOf("a", "removida", "b", "c"))
        val text = unpack(QrPayload.build(set, lib))
        assertEquals("STAGEBOOK1\n3\nShow de sábado\nHazard\nMalandragem\nIt's My Life\tBon Jovi", text)
        assertEquals(
            QrPayload.Parsed("Show de sábado", listOf(QrPayload.Entry("Hazard", ""), QrPayload.Entry("Malandragem", ""), QrPayload.Entry("It's My Life", "Bon Jovi"))),
            QrPayload.parse(text),
        )
        // a title is missing: the reader refuses instead of creating an incomplete setlist
        assertNull(QrPayload.parse("STAGEBOOK1\n3\nShow\nHazard\nMalandragem"))
        // the old format no longer works
        assertNull(QrPayload.parse("LETRAS3\n1\nShow\nHazard"))   // an old format: not read
    }

    @Test
    fun `QR limit counts the compressed size, with the name and extra songs`() {
        // varied titles like real ones (repetitive ones would compress too much and the test would prove nothing)
        val rnd = kotlin.random.Random(5)
        fun word() = (1..rnd.nextInt(3, 9)).map { "abcdefghijklmnopqrstuvwxyzáéíõç"[rnd.nextInt(31)] }.joinToString("")
        val songs = (1..200).map { Song("s$it", (1..rnd.nextInt(1, 4)).joinToString(" ") { word() }, word()) }
        val lib = Library(songs = songs)
        val set = Setlist("s", "Show", songs.take(40).map { it.id })
        assertTrue("40 títulos médios cabem", QrPayload.fits(set, lib))
        assertFalse("200 não cabem", QrPayload.fits(set, lib, extraIds = songs.drop(40).map { it.id }))
        assertFalse("um nome enorme não cabe", QrPayload.fits(set, lib, name = (1..300).joinToString(" ") { word() }))
        assertTrue(QrPayload.build(set, lib).size <= QrPayload.MAX_BYTES)
    }

    @Test
    fun `MusicBrainz picks the oldest release with the same title`() {
        val body = """
            {"recordings":[
              {"score":100,"title":"Hazard","first-release-date":"1997-01-01","artist-credit":[{"name":"Richard Marx"}]},
              {"score":95,"title":"Hazard","first-release-date":"1991-11-05","artist-credit":[{"name":"Richard Marx"}]},
              {"score":90,"title":"Hazard (live)","first-release-date":"1980","artist-credit":[{"name":"Richard Marx"}]},
              {"score":50,"title":"Hazard","first-release-date":"1970","artist-credit":[{"name":"Outro"}]}
            ]}
        """.trimIndent()
        assertEquals(MusicBrainz.Info("Hazard", "Richard Marx", "1991"), MusicBrainz.best(body, "Hazard", ""))
        assertNull(MusicBrainz.best(body, "Hazard", "Legião Urbana"))
    }

    @Test
    fun `pedal keys`() {
        assertEquals(PedalAction.NEXT, PedalAction.of(android.view.KeyEvent.KEYCODE_PAGE_DOWN))
        assertEquals(PedalAction.PREVIOUS, PedalAction.of(android.view.KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(PedalAction.NONE, PedalAction.of(android.view.KeyEvent.KEYCODE_A))
    }

    @Test
    fun `pedal button set to another command in settings`() {
        val a = android.view.KeyEvent.KEYCODE_A
        val down = android.view.KeyEvent.KEYCODE_PAGE_DOWN
        var keys = PedalAction.assign(emptyMap(), a, PedalAction.NEXT_SONG)
        keys = PedalAction.assign(keys, down, PedalAction.PREVIOUS)
        assertEquals(PedalAction.NEXT_SONG, PedalAction.of(a, keys))
        assertEquals(PedalAction.PREVIOUS, PedalAction.of(down, keys))
        // keys with no choice keep the default
        assertEquals(PedalAction.NEXT, PedalAction.of(android.view.KeyEvent.KEYCODE_SPACE, keys))
        // picking the default itself removes the key from the list
        assertEquals(setOf(a), PedalAction.assign(keys, down, PedalAction.NEXT).keys)
        // the choice is saved in the library
        val lib = Library(look = Look(pedalKeys = keys))
        assertEquals(keys, LibraryCodec.decode(LibraryCodec.encode(lib), newId).look.pedalKeys)
    }
}
