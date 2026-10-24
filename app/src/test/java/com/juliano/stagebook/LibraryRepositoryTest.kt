package com.juliano.stagebook

import androidx.test.core.app.ApplicationProvider
import com.juliano.stagebook.data.LibraryCodec
import com.juliano.stagebook.data.LibraryRepository
import com.juliano.stagebook.data.model.Library
import com.juliano.stagebook.data.model.Song
import kotlinx.coroutines.CoroutineExceptionHandler
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
import java.io.File

/** A biblioteca no disco: falha de gravação não derruba o app, arquivo estragado volta da cópia. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class LibraryRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val dir = context.filesDir
    private val crashes = mutableListOf<Throwable>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> synchronized(crashes) { crashes += e } })

    private fun repo() = LibraryRepository(context, scope).also { r -> runBlocking { withTimeout(5_000) { r.loaded.first { it } } } }

    private fun waitWrites() = Thread.sleep(400)

    @Test
    fun `gravação falhando (disco) não derruba o app e a música continua na memória`() {
        val r = repo()
        File(dir, "stagebook.json.tmp").apply { deleteRecursively(); mkdirs() }   // não dá para criar o temporário
        r.saveSong(Song(title = "Hazard", text = "letra"))
        waitWrites()
        assertEquals(emptyList<Throwable>(), crashes)
        assertTrue(r.findSong("Hazard", "") != null)
        File(dir, "stagebook.json.tmp").deleteRecursively()
    }

    @Test
    fun `arquivo estragado - guarda o original e abre a versão anterior`() {
        val r = repo()
        r.saveSong(Song(title = "Primeira", text = "a"))
        waitWrites()
        r.saveSong(Song(title = "Segunda", text = "b"))
        waitWrites()
        File(dir, "stagebook.json").writeText("{ isto não é json")   // desligou no meio de outra coisa
        val again = repo()
        assertTrue("abre a cópia anterior", again.findSong("Primeira", "") != null)
        assertTrue("o estragado fica guardado", dir.listFiles()!!.any { it.name.startsWith("stagebook.corrompido.") })
    }

    @Test
    fun `prévia do import conta as letras que seriam trocadas sem mudar nada`() = runBlocking {
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
    fun `muitas mudanças seguidas acabam gravadas com o estado mais novo`() {
        val r = repo()
        repeat(30) { r.saveSong(Song(title = "Música $it", text = "letra $it")) }
        waitWrites()
        val disk = LibraryCodec.decode(File(dir, "stagebook.json").readText()) { "x" }
        assertEquals(r.library.value.songs.size, disk.songs.size)
        assertTrue(disk.songs.size >= 30)
        assertTrue(Library().songs.isEmpty())
    }
}
