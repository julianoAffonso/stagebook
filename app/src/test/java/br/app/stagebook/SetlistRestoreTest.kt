package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the audit of 16/11 found in a setlist being edited: songs deleted meanwhile, an old setlist's repeats, the count of outside changes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SetlistRestoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val scope = testScope()
    @After fun stop() { scope.cancel() }
    private val repo = testRepo(scope = scope, empty = true)

    @Test fun `Discard doesn't bring back a song deleted meanwhile, and the break moves to the next song that stays`() {
        val (a, b, c) = listOf("A", "B", "C").map { repo.saveSong(Song(title = it)) }
        val set = repo.createSetlist("Show")
        repo.addToSetlist(set, listOf(a, b, c))
        repo.deleteSong(b)
        repo.restoreSetlist(set, listOf(a, b, c), listOf(2))   // as it was when editing began: a break before C
        assertEquals(listOf(a, c), repo.library.value.setlist(set)!!.items)
        assertEquals(listOf(1), repo.library.value.setlist(set)!!.breaks)
    }

    @Test fun `Discard writes an old setlist back with the same song twice`() {
        val (a, b) = listOf("A", "B").map { repo.saveSong(Song(title = it)) }
        val set = repo.createSetlist("Antigo")
        repo.restoreSetlist(set, listOf(a, b, a), emptyList())
        assertEquals(listOf(a, b, a), repo.library.value.setlist(set)!!.items)
    }

    @Test fun `a song checked and deleted before it went in is not added`() {
        val (a, b) = listOf("A", "B").map { repo.saveSong(Song(title = it)) }
        val set = repo.createSetlist("Show")
        repo.deleteSong(b)
        repo.addToSetlist(set, listOf(a, b))
        assertEquals(listOf(a), repo.library.value.setlist(set)!!.items)
    }

    @Test fun `the count of outside changes starts somewhere else in each run, and every mark counts`() {
        val set = repo.createSetlist("Show")
        assertEquals(null, repo.externalEdits.value[set])
        val threads = (1..8).map { Thread { repeat(50) { repo.markExternalEdit(set) } } }.onEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(repo.externalBase + 400, repo.externalEdits.value[set])
        assertNotEquals(0, repo.externalBase)
    }
}
