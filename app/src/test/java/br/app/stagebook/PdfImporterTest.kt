package br.app.stagebook

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.IncomingFiles
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.PdfImporter
import br.app.stagebook.data.PdfInspector
import br.app.stagebook.data.PdfPageCounter
import br.app.stagebook.data.PdfProblem
import br.app.stagebook.data.PdfStore
import br.app.stagebook.data.message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** PDFs that come in as songs (user, 15/11): from the + menu and opened with the app. Robolectric doesn't draw PDFs: a fake counts the pages. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class PdfImporterTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @After fun stop() { scope.cancel() }

    private val repo = LibraryRepository(context, scope)
        .also { r -> runBlocking { withTimeout(5_000) { r.loaded.first { it } }; r.restore(emptyList(), emptyList()) } }
    private val importer = PdfImporter(repo, PdfStore(context), PdfInspector(object : PdfPageCounter { override fun pageCount(file: File) = 3 }))

    private fun pdf(name: String, body: ByteArray = "%PDF-1.7\n".toByteArray() + ByteArray(50)): Uri {
        val dir = File(context.cacheDir, "in-${System.nanoTime()}").apply { mkdirs() }
        return Uri.fromFile(File(dir, name).apply { writeBytes(body) })
    }

    @Test fun `the title comes from the file name`() {
        assertEquals("Wonderwall", PdfImporter.titleOf("Wonderwall.pdf"))
        assertEquals("Garota de Ipanema", PdfImporter.titleOf("Garota_de_Ipanema.PDF"))
        assertEquals("PDF", PdfImporter.titleOf(".pdf"))
        assertEquals("Cifra", PdfImporter.titleOf("Cifra.Pdf"))
        assertEquals("Cifra", PdfImporter.titleOf("Cifra.pdf  "))
        assertEquals("minha cifra", PdfImporter.titleOf("pasta/minha cifra.pdf"))
    }

    @Test fun `each good PDF becomes a song named after the file, with its file kept and no text`() = runBlocking {
        val r = importer.import(listOf(pdf("Cifra Um.pdf"), pdf("Cifra Dois.pdf")))
        assertEquals(listOf("Cifra Um", "Cifra Dois"), r.added)
        assertTrue(r.refused.isEmpty())
        val songs = repo.library.value.songs
        assertEquals(2, songs.size)
        val one = songs.first { it.title == "Cifra Um" }
        assertTrue(one.pdf.isNotEmpty() && File(context.filesDir, "pdfs/${one.pdf}").exists())
        assertEquals("Cifra Um.pdf", one.pdfName)
        assertEquals("", one.text)
    }

    @Test fun `one PDF names its song to open in the editor, several name none`() = runBlocking {
        val one = importer.import(listOf(pdf("Sozinha.pdf")))
        assertEquals(repo.library.value.songs.single { it.title == "Sozinha" }.id, one.single)
        val two = importer.import(listOf(pdf("A.pdf"), pdf("B.pdf")))
        assertEquals(2, two.ids.size)
        assertEquals(null, two.single)
        val bytes = importer.importBytes("Baixada.pdf", "%PDF-1.7\n".toByteArray() + ByteArray(50))
        assertEquals(repo.library.value.songs.single { it.title == "Baixada" }.id, bytes.single)
    }

    @Test fun `a file that is no PDF is left out with the reason and the others still come in`() = runBlocking {
        val r = importer.import(listOf(pdf("Boa.pdf"), pdf("Falsa.pdf", "PK zip".toByteArray())))
        assertEquals(listOf("Boa"), r.added)
        assertEquals(listOf(PdfImporter.Refusal("Falsa.pdf", PdfProblem.NOT_PDF)), r.refused)
        assertEquals(1, repo.library.value.songs.size)
        val say = r.message(context)
        assertTrue(say, say.contains("1 música em PDF adicionada") && say.contains("Falsa.pdf"))
    }

    @Test fun `a name already taken by a song with no artist gets a number`() = runBlocking {
        importer.import(listOf(pdf("Mesma.pdf")))
        val r = importer.import(listOf(pdf("Mesma.pdf")))
        assertEquals(listOf("Mesma (2)"), r.added)
    }

    @Test fun `a PDF that cannot be read says so and makes no song`() = runBlocking {
        val r = importer.import(listOf(Uri.fromFile(File(context.cacheDir, "nowhere.pdf"))))
        assertTrue(r.added.isEmpty())
        assertEquals(PdfProblem.UNREADABLE, r.refused.single().problem)
        assertTrue(repo.library.value.songs.isEmpty())
    }

    @Test fun `only an intent that carries a PDF is a PDF to import`() {
        val uri = Uri.parse("content://some.app/doc/1")
        assertEquals(uri, IncomingFiles.pdfUri(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")))
        assertEquals(uri, IncomingFiles.pdfUri(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)))
        assertNull(IncomingFiles.pdfUri(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/octet-stream")))
        assertNull(IncomingFiles.pdfUri(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("https://x.y/a.pdf"), "application/pdf")))
        assertNull(IncomingFiles.pdfUri(null))
    }

    @Test fun `a file named Artist - Title gives the artist and the title, anything else only the title`() {
        assertEquals("Legião Urbana" to "Pais e Filhos", PdfImporter.artistAndTitleOf("Legião Urbana - Pais e Filhos.pdf"))
        assertEquals("Titãs" to "Epitáfio", PdfImporter.artistAndTitleOf("Titãs_-_Epitáfio.PDF"))
        assertEquals("" to "Garota de Ipanema", PdfImporter.artistAndTitleOf("Garota_de_Ipanema.pdf"))
        // two dashes, or one side empty: no guess
        assertEquals("" to "A - B - C", PdfImporter.artistAndTitleOf("A - B - C.pdf"))
        assertEquals("" to "- Sozinho", PdfImporter.artistAndTitleOf("- Sozinho.pdf"))
    }
}
