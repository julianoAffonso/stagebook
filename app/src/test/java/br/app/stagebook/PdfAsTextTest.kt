package br.app.stagebook

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.PdfInspector
import br.app.stagebook.data.PdfPageCounter
import br.app.stagebook.data.PdfStore
import br.app.stagebook.data.model.Song
import br.app.stagebook.data.web.HttpRequest
import br.app.stagebook.data.web.WebEditorApi
import br.app.stagebook.data.web.WebEditorAuth
import br.app.stagebook.domain.KeptLyrics
import br.app.stagebook.ui.editor.EditorPdf
import br.app.stagebook.ui.editor.PdfSongCard
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** A PDF song's kept lyrics on the stage in place of the PDF, switched on in the PDF card (user, 22/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class PdfAsTextTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun repo() = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO)).also { r -> runBlocking { withTimeout(5_000) { r.loaded.first { it } } } }

    @Test fun `only lyrics or chords count as kept, not directives nor section titles`() {
        assertFalse(KeptLyrics.has(""))
        assertFalse(KeptLyrics.has("{key: G}\n{tempo: 90}\n[Refrão]\n\n"))
        assertTrue(KeptLyrics.has("{key: G}\n[G] [D] [Em]"))
        assertTrue(KeptLyrics.has("[Verso]\nUma linha"))
    }

    @Test fun `the card offers the switch only when the song kept lyrics, and it switches`() {
        var asked: Boolean? = null
        compose.setContent { StagebookTheme { PdfSongCard(EditorPdf("a.pdf", pages = 2, keptLyrics = true), {}, {}, onAsText = { asked = it }) } }
        compose.onNodeWithText("Mostrar a letra no palco").performClick()
        assertEquals(true, asked)
    }

    @Test fun `with nothing kept the card has no switch`() {
        compose.setContent { StagebookTheme { PdfSongCard(EditorPdf("a.pdf", pages = 2, keptLyrics = false), {}, {}) } }
        compose.onNodeWithText("Mostrar a letra no palco").assertDoesNotExist()
    }

    @Test fun `switched on, the stage gets the song as lyrics, and taking the PDF out turns it off`() = runBlocking {
        val repo = repo()
        val id = repo.saveSong(Song(title = "Partitura", text = "[G]letra guardada"))
        PdfStore(context).file("p.pdf").apply { parentFile?.mkdirs(); writeBytes("%PDF-1.4\n".toByteArray()) }
        repo.updateSong(id) { it.copy(pdf = "p.pdf", pdfName = "Partitura.pdf", pdfText = true) }
        val vm = testStage(context, repo, SavedStateHandle(mapOf("songId" to id, "start" to 0)))
        val shown = withTimeout(5_000) { vm.uiState.first { it.songs.isNotEmpty() && !it.loading } }.songs.single()
        assertEquals("", shown.pdf)
        assertEquals("[G]letra guardada", shown.text)
        // switched off, the stage opened again shows the PDF
        repo.updateSong(id) { it.copy(pdfText = false) }
        val again = testStage(context, repo, SavedStateHandle(mapOf("songId" to id, "start" to 0)))
        assertEquals("p.pdf", withTimeout(5_000) { again.uiState.first { it.songs.isNotEmpty() && !it.loading } }.songs.single().pdf)
        assertFalse(repo.song(id)!!.withoutPdf().pdfText)
    }

    @Test fun `the editor on the computer has the same switch, offered and kept the same way`() {
        val repo = repo()
        val pdfs = PdfStore(context)
        val api = WebEditorApi(repo, pdfs = { pdfs }, inspector = PdfInspector(object : PdfPageCounter { override fun pageCount(file: File) = 2 }), workDir = { context.cacheDir })
        val device = WebEditorAuth.Device("d", "Chrome · Windows", "h", 0)
        fun call(method: String, path: String, body: String = "") = api.handle(HttpRequest(method, path, emptyMap(), body = body.toByteArray()), device)!!
        val id = repo.saveSong(Song(title = "Partitura", text = "[G]letra guardada"))
        repo.updateSong(id) { it.copy(pdf = "p.pdf", pdfName = "Partitura.pdf") }
        assertTrue(call("GET", "/api/songs/$id").text().let { "\"keptLyrics\":true" in it })
        assertEquals(200, call("PUT", "/api/songs/$id/pdf-view", """{"text":true}""").status)
        assertTrue(repo.song(id)!!.pdfText)
        // the invert switch alone leaves it as it was
        call("PUT", "/api/songs/$id/pdf-view", """{"invert":true}""")
        assertTrue(repo.song(id)!!.pdfText)
    }
}
