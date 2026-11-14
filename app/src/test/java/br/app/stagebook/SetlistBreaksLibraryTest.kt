package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.Backup
import br.app.stagebook.data.Backup.Part
import br.app.stagebook.data.LibraryCodec
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.TrackStore
import br.app.stagebook.data.model.Library
import br.app.stagebook.data.model.Setlist
import br.app.stagebook.data.model.Song
import br.app.stagebook.data.web.HttpRequest
import br.app.stagebook.data.web.WebEditorApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** A setlist's breaks in the library: whatever changes its songs leaves every break between two songs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SetlistBreaksLibraryTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun repo() = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        .also { r -> runBlocking { withTimeout(5_000) { r.loaded.first { it } }; r.restore(emptyList(), emptyList()) } }

    private fun LibraryRepository.set(id: String) = library.value.setlist(id)!!

    private fun LibraryRepository.songs(vararg titles: String) = titles.map { saveSong(Song(title = it, author = "x", text = it)) }

    /** A B | C D E, the break before C. */
    private fun LibraryRepository.show(): Pair<String, List<String>> {
        val ids = songs("A", "B", "C", "D", "E")
        val s = createSetlist("Show")
        addToSetlist(s, ids)
        addBreak(s)          // before E
        moveRow(s, 4, 2)     // dragged up to before C
        return s to ids
    }

    @Test fun `a new break goes before the last song and can be dragged into place`() {
        val r = repo()
        val ids = r.songs("A", "B", "C")
        val s = r.createSetlist("Show")
        r.addBreak(s)   // no songs yet: nowhere to put it
        assertEquals(emptyList<Int>(), r.set(s).breaks)
        r.addToSetlist(s, ids)
        r.addBreak(s)
        assertEquals(listOf(2), r.set(s).breaks)
        r.moveRow(s, 2, 1)   // rows: A B | C → A | B C
        assertEquals(listOf(1), r.set(s).breaks)
        assertEquals(ids, r.set(s).items)
        r.removeBreak(s, 1)
        assertEquals(emptyList<Int>(), r.set(s).breaks)
    }

    // the Break button is off with fewer than two songs; even a call that gets through leaves the setlist as it was
    @Test fun `a break asked for in a setlist of one song leaves nothing`() {
        val r = repo()
        val s = r.createSetlist("Show")
        r.addToSetlist(s, r.songs("A"))
        val before = r.set(s)
        r.addBreak(s)
        assertEquals(before, r.set(s))
    }

    @Test fun `dragging a song past the break keeps the songs and moves it to the other block`() {
        val r = repo()
        val (s, ids) = r.show()
        val (a, b, c, d, e) = ids
        assertEquals(listOf(2), r.set(s).breaks)
        r.moveRow(s, 0, 3)   // rows A B | C D E: A dropped after C
        assertEquals(listOf(b, c, a, d, e), r.set(s).items)
        assertEquals(listOf(1), r.set(s).breaks)
    }

    @Test fun `moving, removing and adding songs keep the break between the same songs`() {
        val r = repo()
        val (s, ids) = r.show()
        val (a, b, c, d, e) = ids
        r.moveItem(s, 4, 3)   // E before D, both after the break
        assertEquals(listOf(a, b, c, e, d), r.set(s).items)
        assertEquals(listOf(2), r.set(s).breaks)
        r.removeItem(s, 0, a)
        assertEquals(listOf(1), r.set(s).breaks)   // still before C
        r.addToSetlist(s, r.songs("F"))
        assertEquals(listOf(1), r.set(s).breaks)
        r.removeItem(s, 0, b)   // nothing left before the break: it goes
        assertEquals(emptyList<Int>(), r.set(s).breaks)
    }

    @Test fun `deleting songs from the library keeps the breaks valid`() {
        val r = repo()
        val (s, ids) = r.show()
        r.deleteSongs(listOf(ids[1], ids[2]))   // B and C: A | D E
        assertEquals(listOf(ids[0], ids[3], ids[4]), r.set(s).items)
        assertEquals(listOf(1), r.set(s).breaks)
        r.deleteSongs(listOf(ids[3], ids[4]))   // nothing after the break
        assertEquals(emptyList<Int>(), r.set(s).breaks)
    }

    @Test fun `replacing the songs keeps the break before the same song`() {
        val r = repo()
        val (s, ids) = r.show()
        val (a, b, c, d, _) = ids
        r.setSetlistItems(s, listOf(d, c, b, a))
        assertEquals(listOf(1), r.set(s).breaks)   // before C
        r.setSetlistItems(s, listOf(a))
        assertEquals(emptyList<Int>(), r.set(s).breaks)
    }

    // the editor on the computer doesn't know breaks yet: saving a setlist from it can't leave one out of place
    @Test fun `a setlist saved from the computer ends with valid breaks`() {
        val r = repo()
        val (s, ids) = r.show()
        val (a, b, c, d, e) = ids
        val api = WebEditorApi(r)
        val device = br.app.stagebook.data.web.WebEditorAuth.Device("d", "Chrome · Windows", "h", 0)
        fun put(items: List<String>) = api.handle(
            HttpRequest("PUT", "/api/sets/$s", body = """{"name":"Show","items":[${items.joinToString(",") { "\"$it\"" }}]}""".toByteArray()), device,
        )!!
        assertEquals(200, put(listOf(e, d, c, b, a)).status)   // reordered
        assertEquals(listOf(e, d, c, b, a), r.set(s).items)
        assertEquals(listOf(2), r.set(s).breaks)   // still before C
        assertEquals(200, put(listOf(c, a)).status)   // C first: the break has no song before it
        assertEquals(emptyList<Int>(), r.set(s).breaks)
        assertEquals(200, put(emptyList()).status)
        assertEquals(emptyList<Int>(), r.set(s).breaks)
    }

    @Test fun `a backup brings the breaks back`() = runBlocking {
        val r = repo()
        val (s, ids) = r.show()
        val backup = Backup(context, r, TrackStore(context))
        val out = ByteArrayOutputStream()
        backup.write(out, setOf(Part.LIBRARY))
        r.restore(emptyList(), emptyList())
        assertTrue(backup.restore(ByteArrayInputStream(out.toByteArray()), setOf(Part.LIBRARY)))
        assertEquals(ids, r.set(s).items)
        assertEquals(listOf(2), r.set(s).breaks)
        // joined to a library that already has those songs: the new setlist points to them, with its break
        r.renameSetlist(s, "Another")
        assertTrue(backup.restore(ByteArrayInputStream(out.toByteArray()), setOf(Part.LIBRARY), replace = false))
        val joined = r.library.value.sets.first { it.name == "Show" }
        assertEquals(ids, joined.items)
        assertEquals(listOf(2), joined.breaks)
    }

    @Test fun `breaks that do not fit are dropped when the library is read or restored`() {
        val text = LibraryCodec.encode(Library(songs = listOf(Song(id = "a", title = "A"), Song(id = "b", title = "B")), sets = listOf(Setlist("s", "Show", listOf("a", "b"), listOf(5, 1, 0, 1)))))
        assertEquals(listOf(1), LibraryCodec.decode(text) { "new" }.sets.single().breaks)
        // a setlist saved before breaks existed reads as before
        assertEquals(emptyList<Int>(), LibraryCodec.decode("""{"sets":[{"id":"s","name":"Show","items":["a"]}]}""") { "new" }.sets.single().breaks)
        val r = repo()
        runBlocking { r.restore(emptyList(), listOf(Setlist("s", "Show", listOf("a", "b", "c"), listOf(3, 2, 2)))) }
        assertEquals(listOf(2), r.set("s").breaks)
    }
}
