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

    // IMSLP freezes on a phone (user, 22/11): off the suggested list and off a list a person saved with it
    @Test fun `IMSLP is off the PDF sites, also from a saved list`() {
        assertFalse(ImportSites.PDF.any { "imslp" in it.url })
        // CPDL in its place: free choral scores, the PDF a direct link, no account (imported on the S26, 22/11)
        assertTrue(ImportSites.PDF.any { it.name == "CPDL" && it.url.startsWith("https://www.cpdl.org/") })
        val saved = Look(pdfSites = listOf(
            br.app.stagebook.data.model.ImportSite("IMSLP", "https://imslp.org/"),
            br.app.stagebook.data.model.ImportSite("Meu", "https://meu.example/"),
        ))
        assertEquals(listOf("Meu"), saved.sites(SiteKind.PDF).map { it.name })
        // a list that had only IMSLP gets the suggested one, never an empty browser
        val only = Look(pdfSites = listOf(br.app.stagebook.data.model.ImportSite("IMSLP", "https://imslp.org/")))
        assertEquals(ImportSites.PDF, only.sites(SiteKind.PDF))
    }

    @Test fun `a person's own PDF list is kept and old libraries get the suggested one`() {
        val own = Look(pdfSites = listOf(br.app.stagebook.data.model.ImportSite("Meu", "https://meu.example/")))
        assertEquals("Meu", own.sites(SiteKind.PDF).single().name)
        val old = lenientJson.decodeFromString(Look.serializer(), "{}")
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

    /** A connection that answers what the test says, and remembers the headers it was given. */
    private class Fake(url: String, val code: Int, val location: String? = null, val body: ByteArray = ByteArray(0), val boom: Boolean = false) : java.net.HttpURLConnection(java.net.URL(url)) {
        val sent = HashMap<String, String>()
        override fun setRequestProperty(key: String, value: String) { if (boom) throw IllegalArgumentException("bad header"); sent[key] = value }
        override fun getResponseCode() = code
        override fun getHeaderField(name: String): String? = if (name == "Location") location else null
        override fun getContentLengthLong() = body.size.toLong()
        override fun getInputStream() = body.inputStream()
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy() = false
    }

    @Test fun `only https is fetched, also after a redirect`() = runBlocking {
        assertEquals(PdfDownload.Fetched.Failed, PdfDownload.fetch("http://x.org/a.pdf", null, null, { null }))
        assertEquals(PdfDownload.Fetched.Failed, PdfDownload.fetch("file:///etc/hosts", null, null, { null }))
        val hops = ArrayList<String>()
        val r = PdfDownload.fetch("https://a.org/a.pdf", null, null, { null }, connect = { hops += it; Fake(it, 302, "http://b.org/a.pdf") })
        assertEquals(PdfDownload.Fetched.Failed, r)
        assertEquals(listOf("https://a.org/a.pdf"), hops)
    }

    @Test fun `a redirect to another host gets that host's cookies, never the first site's`() = runBlocking {
        val made = ArrayList<Fake>()
        val r = PdfDownload.fetch("https://a.org/get?id=1", "Agent", null, { if ("a.org" in it) "session=secret" else null }, connect = {
            (if ("a.org" in it) Fake(it, 302, "https://cdn.b.org/files/Valsa.pdf") else Fake(it, 200, body = pdf)).also { f -> made += f }
        })
        assertTrue(r is PdfDownload.Fetched.Bytes)
        assertEquals("Valsa.pdf", (r as PdfDownload.Fetched.Bytes).name)
        assertEquals("session=secret", made[0].sent["Cookie"])
        assertEquals(null, made[1].sent["Cookie"])
        assertEquals("Agent", made[1].sent["User-Agent"])
    }

    @Test fun `redirects without end, a refused header and a server error all fail without closing the app`() = runBlocking {
        assertEquals(PdfDownload.Fetched.Failed, PdfDownload.fetch("https://a.org/a.pdf", null, null, { null }, connect = { Fake(it, 302, "https://a.org/a.pdf") }))
        assertEquals(PdfDownload.Fetched.Failed, PdfDownload.fetch("https://a.org/a.pdf", null, null, { "c=\u00e9" }, connect = { Fake(it, 200, body = pdf, boom = true) }))
        assertEquals(PdfDownload.Fetched.Failed, PdfDownload.fetch("https://a.org/a.pdf", null, null, { null }, connect = { Fake(it, 404) }))
        assertEquals(PdfDownload.Fetched.TooBig, PdfDownload.fetch("https://a.org/a.pdf", null, null, { null }, connect = { Fake(it, 200, body = ByteArray(PdfStore.MAX_BYTES.toInt() + 1)) }))
    }

    @Test fun `a PDF served under another type is taken by its name, and a name is cut to what can be shared`() {
        assertTrue(PdfDownload.isPdf("https://x.org/dl/Valsa.pdf", "application/force-download", null))
        assertTrue(PdfDownload.isPdf("https://x.org/dl?id=2", "application/x-download", "attachment; filename=Valsa.pdf"))
        assertFalse(PdfDownload.isPdf("https://x.org/dl/pack.zip", "application/zip", null))
        val long = PdfDownload.nameOf("https://x.org/" + "a".repeat(400) + "%0A.pdf", null)
        assertTrue(long.length <= br.app.stagebook.domain.BandContent.MAX_PDF_NAME && long.none { it.isISOControl() })
    }

    @Test fun `a download waits for the person's yes, one at a time, and what is not a PDF is said`() = runBlocking {
        val vm = br.app.stagebook.ui.importer.PdfSiteViewModel(importer)
        vm.offered("https://x.org/pack.zip", null, null, "application/zip")
        assertEquals(br.app.stagebook.ui.importer.PdfSiteViewModel.Outcome.NotPdf, withTimeout(2_000) { vm.outcome.first() })
        assertEquals(null, vm.asking.value)
        vm.offered("https://x.org/Valsa.pdf", null, null, "application/pdf")
        assertEquals("Valsa.pdf", vm.asking.value?.name)
        // a page that fires more downloads while the question is open adds nothing
        vm.offered("https://x.org/Outra.pdf", null, null, "application/pdf")
        assertEquals("Valsa.pdf", vm.asking.value?.name)
        vm.decline()
        assertEquals(null, vm.asking.value)
        assertTrue("nothing came in without a yes", repo.library.value.songs.isEmpty())
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
