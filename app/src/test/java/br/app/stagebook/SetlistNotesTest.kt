package br.app.stagebook

import br.app.stagebook.data.model.Setlist
import br.app.stagebook.data.model.Song
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A short note per song in a setlist ("enters a cappella"; user, 24/11): what is kept, what goes with the setlist. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SetlistNotesTest {
    private fun repo() = testRepo(empty = true)

    @Test fun `a note is one trimmed line of at most 140 characters`() {
        assertEquals("entra a capela", Setlist.cleanNote("  entra \n  a   capela \n"))
        assertEquals(140, Setlist.cleanNote("x".repeat(500)).length)
        assertEquals("", Setlist.cleanNote(" \n \t"))
    }

    @Test fun `the note is kept for a song of the setlist and an empty one takes it out`() = runBlocking {
        val repo = repo()
        val a = repo.saveSong(Song(title = "A", text = "x"))
        val set = repo.createSetlist("Show").also { repo.addToSetlist(it, listOf(a)) }
        repo.setNote(set, a, " entra a capela ")
        assertEquals("entra a capela", repo.library.value.setlist(set)!!.noteFor(a))
        repo.setNote(set, a, "  ")
        assertEquals("", repo.library.value.setlist(set)!!.noteFor(a))
        assertTrue(repo.library.value.setlist(set)!!.notes.isEmpty())
    }

    @Test fun `a song that is not in the setlist gets no note`() = runBlocking {
        val repo = repo()
        val a = repo.saveSong(Song(title = "A", text = "x"))
        val b = repo.saveSong(Song(title = "B", text = "x"))
        val set = repo.createSetlist("Show").also { repo.addToSetlist(it, listOf(a)) }
        repo.setNote(set, b, "não deve ficar")
        assertTrue(repo.library.value.setlist(set)!!.notes.isEmpty())
    }

    @Test fun `a song taken out keeps its note out of sight, and it comes back with the song`() = runBlocking {
        val repo = repo()
        val a = repo.saveSong(Song(title = "A", text = "x"))
        val b = repo.saveSong(Song(title = "B", text = "x"))
        val set = repo.createSetlist("Show").also { repo.addToSetlist(it, listOf(a, b)) }
        repo.setNote(set, a, "tom abaixo")
        repo.removeItem(set, 0, a)
        val without = repo.library.value.setlist(set)!!
        assertEquals("", without.noteFor(a))              // not shown for a song that isn't there
        assertTrue(without.travellingNotes().isEmpty())   // nor sent
        repo.addToSetlist(set, listOf(a))
        assertEquals("tom abaixo", repo.library.value.setlist(set)!!.noteFor(a))
    }

    @Test fun `only the notes of the setlist's songs travel, clean`() {
        val set = Setlist(id = "s", name = "Show", items = listOf("a", "b"), notes = mapOf("a" to "  um \n dois ", "b" to " ", "gone" to "fora"))
        assertEquals(mapOf("a" to "um dois"), set.travellingNotes())
    }

    @Test fun `a copy of the setlist keeps the notes`() = runBlocking {
        val repo = repo()
        val a = repo.saveSong(Song(title = "A", text = "x"))
        val set = repo.createSetlist("Show").also { repo.addToSetlist(it, listOf(a)) }
        repo.setNote(set, a, "capela")
        val copy = repo.duplicateSetlist(set, "Show (cópia)")!!
        assertEquals("capela", repo.library.value.setlist(copy)!!.noteFor(a))
    }
}
