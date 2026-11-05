package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LibraryCodec
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.Library
import br.app.stagebook.data.model.Song
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The library on disk: a write failure doesn't crash the app, a corrupted file comes back from the copy. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class LibraryRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val dir = context.filesDir
    private val crashes = mutableListOf<Throwable>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> synchronized(crashes) { crashes += e } })

    private fun repo() = LibraryRepository(context, scope).also { r -> runBlocking { withTimeout(5_000) { r.loaded.first { it } } } }

    private fun waitWrites() = Thread.sleep(400)

    private fun waitSaved(name: String, text: String) = runBlocking {
        withTimeout(10_000) { while (File(dir, name).takeIf { it.exists() }?.readText()?.contains(text) != true) delay(20) }
    }

    @Test
    fun `failing write (disk) does not crash the app and the song stays in memory`() {
        val r = repo()
        File(dir, "stagebook.json.tmp").apply { deleteRecursively(); mkdirs() }   // can't create the temp file
        r.saveSong(Song(title = "Hazard", text = "letra"))
        waitWrites()
        assertEquals(emptyList<Throwable>(), crashes)
        assertTrue(r.findSong("Hazard", "") != null)
        File(dir, "stagebook.json.tmp").deleteRecursively()
    }

    @Test
    fun `corrupted file - keeps the original and opens the previous version`() {
        val r = repo()
        r.saveSong(Song(title = "Primeira", text = "a"))
        waitSaved("stagebook.json", "Primeira")
        r.saveSong(Song(title = "Segunda", text = "b"))
        // a save still on its way would land on top of the damaged file: a fixed wait lost this race on a slow machine
        waitSaved("stagebook.json", "Segunda")
        File(dir, "stagebook.json").writeText("{ isto não é json")   // powered off in the middle of something else
        val again = repo()
        assertTrue("abre a cópia anterior", again.findSong("Primeira", "") != null)
        assertTrue("o estragado fica guardado", dir.listFiles()!!.any { it.name.startsWith("stagebook.corrompido.") })
    }

    @Test
    fun `import preview counts the lyrics that would be replaced without changing anything`() = runBlocking {
        val r = repo()
        r.saveSong(Song(title = "Hazard", author = "Richard Marx", text = "letra editada depois do backup"))
        r.saveSong(Song(title = "Africa", author = "Toto", text = "igual"))
        val file = LibraryCodec.encodeSongs(listOf(
            Song(title = "Hazard", author = "Richard Marx", text = "letra antiga do arquivo"),
            Song(title = "Africa", author = "Toto", text = "igual"),
            Song(title = "Nova", author = "X", text = "nova"),
        ))
        val p = r.previewImport(file)
        assertEquals(1, p.added)
        assertEquals(2, p.replaced)
        assertEquals(1, p.lyricsChanged)
        assertEquals("letra editada depois do backup", r.findSong("Hazard", "Richard Marx")!!.text)
    }

    @Test
    fun `many changes in a row end up saved with the newest state`() {
        val r = repo()
        repeat(30) { r.saveSong(Song(title = "Música $it", text = "letra $it")) }
        waitSaved("stagebook.json", "Música 29")
        val disk = LibraryCodec.decode(File(dir, "stagebook.json").readText()) { "x" }
        assertEquals(r.library.value.songs.size, disk.songs.size)
        assertTrue(disk.songs.size >= 30)
        assertTrue(Library().songs.isEmpty())
    }
}
