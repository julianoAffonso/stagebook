package br.app.stagebook

import br.app.stagebook.data.model.Library
import br.app.stagebook.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Songs by id in one step, always the current list's (deep audit, 30/10). */
class LibraryLookupTest {
    @Test fun `finds by id and follows a changed library`() {
        val lib = Library(songs = (1..2000).map { Song(id = "s$it", title = "Música $it") })
        assertEquals("Música 1500", lib.song("s1500")!!.title)
        assertNull(lib.song("nope"))
        val changed = lib.copy(songs = lib.songs.map { if (it.id == "s1500") it.copy(title = "Nova") else it })
        assertEquals("Nova", changed.song("s1500")!!.title)
        assertEquals("Música 1500", lib.song("s1500")!!.title)
    }
}
