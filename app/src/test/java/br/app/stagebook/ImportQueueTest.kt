package br.app.stagebook

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.ImportQueue
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.ImportSite
import br.app.stagebook.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Importar em lote as que faltam do QR: cada uma entra no lugar dela; sem achar num site, passa para o próximo. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ImportQueueTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun repo() = runBlocking {
        LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO)).also { r -> withTimeout(5_000) { r.loaded.first { it } } }
    }

    @Test fun `imported songs take the place they had in the QR`() {
        val repo = repo()
        val a = repo.saveSong(Song(title = "Primeira"))
        val c = repo.saveSong(Song(title = "Terceira"))
        val set = repo.createSetlist("Show")
        val q = ImportQueue(repo)
        q.start(set, "Show", listOf(a, null, c, null), listOf(ImportQueue.Item("Segunda", "", 1), ImportQueue.Item("Quarta", "", 3)))
        assertEquals(1, q.state.value!!.position)
        val b = repo.saveSong(Song(title = "Segunda"))
        q.saved(b)
        assertEquals(listOf(a, b, c), repo.library.value.setlist(set)!!.items)
        assertEquals("Quarta", q.state.value!!.current!!.title)
        q.skip()   // a quarta fica de fora e a fila acaba
        assertNull(q.state.value)
        assertEquals(listOf(a, b, c), repo.library.value.setlist(set)!!.items)
    }

    @Test fun `not found on a site moves to the next one in the profile's list and the next song goes back to the first`() {
        val repo = repo()
        repo.updateLook { it.copy(chords = true, chordSites = listOf(ImportSite("Um", "https://www.um.com.br/"), ImportSite("Dois", "https://dois.net/"))) }
        val q = ImportQueue(repo)
        q.start("x", "Show", listOf(null, null), listOf(ImportQueue.Item("Música A", "Banda", 0), ImportQueue.Item("Música B", "", 1)))
        assertEquals("site:www.um.com.br Música A Banda", Uri.parse(q.searchUrl()).getQueryParameter("q"))
        assertEquals("Dois", q.nextSite()?.name)
        q.tryNextSite()
        assertEquals("site:dois.net Música A Banda", Uri.parse(q.searchUrl()).getQueryParameter("q"))
        assertNull(q.nextSite())   // era o último
        q.skip()
        assertEquals("site:www.um.com.br Música B", Uri.parse(q.searchUrl()).getQueryParameter("q"))
    }
}
