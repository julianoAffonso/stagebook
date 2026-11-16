package br.app.stagebook

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.PdfDownload
import br.app.stagebook.data.PdfImporter
import br.app.stagebook.data.PdfInspector
import br.app.stagebook.data.PdfPageCounter
import br.app.stagebook.data.PdfProblem
import br.app.stagebook.data.PdfStore
import br.app.stagebook.data.model.ImportSites
import br.app.stagebook.data.model.Look
import br.app.stagebook.data.model.SiteKind
import br.app.stagebook.data.model.sites
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Score sites in PDF (user, 15/11): the suggested list, and what the importer does with a PDF the person tapped. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class PdfSitesTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @After fun stop() { scope.cancel() }
    private val repo = LibraryRepository(context, scope)
        .also { r -> runBlocking { withTimeout(5_000) { r.loaded.first { it } }; r.restore(emptyList(), emptyList()) } }
    private val importer = PdfImporter(repo, PdfStore(context), PdfInspector(object : PdfPageCounter { override fun pageCount(file: File) = 2 }))
    private val pdf = "%PDF-1.7\n".toByteArray() + ByteArray(80)

    @Test fun `the suggested PDF sites are https, not repeated, and open first on the free scores`() {
        val urls = ImportSites.PDF.map { it.url }
        assertTrue(urls.all { it.startsWith("https://") })
        assertEquals(urls.size, urls.toSet().size)
        assertEquals("Mutopia Project", ImportSites.PDF.first().name)
        assertEquals(ImportSites.PDF, Look().sites(SiteKind.PDF))
        // the lists of text sites didn't change
        assertTrue(Look().sites(SiteKind.CHORDS).any { it.name == "Cifra Club" })
        assertFalse(Look().sites(SiteKind.LYRICS).any { it in ImportSites.PDF })
    }

    @Test fun `a person's own PDF list is kept and old libraries get the suggested one`() {
        val own = Look(pdfSites = listOf(br.app.stagebook.data.model.ImportSite("Meu", "https://meu.example/")))
        assertEquals("Meu", own.sites(SiteKind.PDF).single().name)
        val old = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString(Look.serializer(), "{}")
        assertEquals(ImportSites.PDF, old.pdfSites)
    }

    @Test fun `a download is a PDF by its type, or by its name when the type says nothing`() {
        assertTrue(PdfDownload.isPdf("https://x.org/a/b", "application/pdf", null))
        assertTrue(PdfDownload.isPdf("https://x.org/a/b", "Application/PDF; charset=binary", null))
        assertTrue(PdfDownload.isPdf("https://x.org/a/score.pdf", "application/octet-stream", null))
        assertTrue(PdfDownload.isPdf("https://x.org/get?id=3", "", "attachment; filename=\"score.pdf\""))
        assertFalse("an APK is never taken", PdfDownload.isPdf("https://x.org/app.apk", "application/vnd.android.package-archive", null))
        assertFalse(PdfDownload.isPdf("https://x.org/page", "text/html", null))
        assertFalse("a page named .pdf with a page type", PdfDownload.isPdf("https://x.org/a.pdf", "text/html", null))
    }

    @Test fun `only https is fetched`() = runBlocking {
        assertEquals(PdfDownload.Fetched.Failed, PdfDownload.fetch("http://x.org/a.pdf", null, null, null, "application/pdf"))
        assertEquals(PdfDownload.Fetched.Failed, PdfDownload.fetch("file:///etc/hosts", null, null, null, "application/pdf"))
        Unit
    }

    @Test fun `a PDF that came as bytes becomes a song named after its file, and a bad one is refused with the reason`() = runBlocking {
        val ok = importer.importBytes("Autor - Valsa.pdf", pdf)
        assertEquals(listOf("Valsa"), ok.added)
        assertEquals("Autor", repo.findSong("Valsa", "Autor")?.author)
        assertTrue(repo.findSong("Valsa", "Autor")!!.pdf.isNotEmpty())
        val bad = importer.importBytes("falso.pdf", "<html>not a pdf</html>".toByteArray())
        assertTrue(bad.added.isEmpty())
        assertEquals(PdfProblem.NOT_PDF, bad.refused.single().problem)
        // the same title again doesn't overwrite
        assertEquals(listOf("Valsa (2)"), importer.importBytes("Autor - Valsa.pdf", pdf).added.map { it.removePrefix("Autor - ") })
    }

    @Test fun `the file name comes from the header, else from the address, with the extension kept`() {
        assertEquals("Valsa n 1.pdf", PdfDownload.nameOf("https://x.org/get?id=3", "attachment; filename=\"Valsa%20n%201.pdf\""))
        assertEquals("Valsa.pdf", PdfDownload.nameOf("https://x.org/files/Valsa.pdf?dl=1#p2", null))
        assertEquals("PDF", PdfDownload.nameOf("https://x.org/", null))
    }

    @get:org.junit.Rule val compose = androidx.compose.ui.test.junit4.createComposeRule()

    @Test fun `the plus menu offers PDFs from sites beside Import PDFs and taps open the browser`() {
        var sites = 0
        compose.setContent {
            br.app.stagebook.ui.theme.StagebookTheme {
                br.app.stagebook.ui.share.AddMenuButton(true, {}, {}, onPdfs = {}, onPdfSites = { sites++ })
            }
        }
        compose.onNodeWithContentDescription("Nova e importar").performClick()
        compose.onNodeWithText("Importar PDFs").assertIsDisplayed()
        compose.onNodeWithText("PDFs de sites").performClick()
        assertEquals(1, sites)
    }
}
