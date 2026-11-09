package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Editing songs and setlists in the library: deletes, moves and replacements keep the setlists consistent. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class LibraryEditsTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun repo() = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        .also { r -> runBlocking { withTimeout(5_000) { r.loaded.first { it } } } }

    private fun LibraryRepository.items(setId: String) = library.value.setlist(setId)!!.items

    private fun LibraryRepository.three(): Triple<String, String, String> =
        Triple(saveSong(Song(title = "A", text = "a")), saveSong(Song(title = "B", text = "b")), saveSong(Song(title = "C", text = "c")))

    @Test fun `deleting songs takes them out of every setlist`() {
        val r = repo()
        val (a, b, c) = r.three()
        val s1 = r.createSetlist("Show 1").also { r.addToSetlist(it, listOf(a, b, c)) }
        val s2 = r.createSetlist("Show 2").also { r.addToSetlist(it, listOf(b, a)) }
        r.deleteSongs(listOf(a, b))
        assertEquals(listOf(c), r.items(s1))
        assertEquals(emptyList<String>(), r.items(s2))
        assertNull(r.song(a))
        assertNotNull(r.song(c))
    }

    @Test fun `deleting one song leaves the others`() {
        val r = repo()
        val (a, b, _) = r.three()
        val s = r.createSetlist("Show").also { r.addToSetlist(it, listOf(a, b)) }
        r.deleteSong(a)
        assertEquals(listOf(b), r.items(s))
        assertEquals(2, r.library.value.songs.size)
    }

    @Test fun `a new setlist goes on top`() {
        val r = repo()
        r.createSetlist("Old")
        val new = r.createSetlist("New")
        assertEquals(new, r.library.value.sets.first().id)
    }

    @Test fun `adding songs skips the ones already there and repeated ones`() {
        val r = repo()
        val (a, b, c) = r.three()
        val s = r.createSetlist("Show").also { r.addToSetlist(it, listOf(a)) }
        r.addToSetlist(s, listOf(b, a, c, b))
        assertEquals(listOf(a, b, c), r.items(s))
    }

    @Test fun `moving a song in the setlist, and out of range does nothing`() {
        val r = repo()
        val (a, b, c) = r.three()
        val s = r.createSetlist("Show").also { r.addToSetlist(it, listOf(a, b, c)) }
        r.moveItem(s, 0, 2)
        assertEquals(listOf(b, c, a), r.items(s))
        r.moveItem(s, 2, 0)
        assertEquals(listOf(a, b, c), r.items(s))
        r.moveItem(s, 0, 3)
        r.moveItem(s, -1, 0)
        assertEquals(listOf(a, b, c), r.items(s))
    }

    @Test fun `removing checks it is still the same song in that place (double tap)`() {
        val r = repo()
        val (a, b, c) = r.three()
        val s = r.createSetlist("Show").also { r.addToSetlist(it, listOf(a, b, c)) }
        r.removeItem(s, 1, b)
        assertEquals(listOf(a, c), r.items(s))
        r.removeItem(s, 1, b)   // the second tap: position 1 is now C, which stays
        assertEquals(listOf(a, c), r.items(s))
        r.removeItem(s, 5, c)
        assertEquals(listOf(a, c), r.items(s))
    }

    @Test fun `replacing a setlist's songs drops repeats`() {
        val r = repo()
        val (a, b, c) = r.three()
        val s = r.createSetlist("Show").also { r.addToSetlist(it, listOf(a)) }
        r.setSetlistItems(s, listOf(c, b, c))
        assertEquals(listOf(c, b), r.items(s))
    }

    @Test fun `renaming and deleting a setlist touches only that one`() {
        val r = repo()
        val s1 = r.createSetlist("One")
        val s2 = r.createSetlist("Two")
        r.renameSetlist(s1, "First")
        assertEquals("First", r.library.value.setlist(s1)!!.name)
        assertEquals("Two", r.library.value.setlist(s2)!!.name)
        r.deleteSetlist(s2)
        assertNull(r.library.value.setlist(s2))
        assertNotNull(r.library.value.setlist(s1))
    }

    @Test fun `changing a selection changes only the selected songs`() {
        val r = repo()
        val (a, b, c) = r.three()
        r.updateSongs(listOf(a, c)) { it.copy(tags = listOf("Rock")) }
        assertEquals(listOf("Rock"), r.song(a)!!.tags)
        assertEquals(emptyList<String>(), r.song(b)!!.tags)
        assertEquals(listOf("Rock"), r.song(c)!!.tags)
    }

    @Test fun `saving with an existing id replaces the song instead of adding one`() {
        val r = repo()
        val id = r.saveSong(Song(title = "Hazard", text = "v1"))
        assertEquals(id, r.saveSong(r.song(id)!!.copy(text = "v2")))
        assertEquals(1, r.library.value.songs.size)
        assertEquals("v2", r.song(id)!!.text)
    }

    @Test fun `a setlist QR exists only for a setlist that exists`() {
        val r = repo()
        val (a, _, _) = r.three()
        val s = r.createSetlist("Show").also { r.addToSetlist(it, listOf(a)) }
        assertNotNull(r.qrPayload(s))
        assertNull(r.qrPayload("missing"))
    }
}
