package br.app.stagebook

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.BandLink
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.PdfPages
import br.app.stagebook.data.PdfSource
import br.app.stagebook.data.PdfStore
import br.app.stagebook.data.SetlistCloud
import br.app.stagebook.data.model.Song
import br.app.stagebook.ui.stage.PdfStageView
import br.app.stagebook.ui.stage.rememberPdfOpen
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.CancellationException
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** What the audit of 15/11 found in the PDF code: a name forged by a band member, a PDF closed under the stage, a failing page. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class PdfAuditFixesTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @After fun stop() { scope.cancel() }

    private fun repo() = LibraryRepository(context, scope)
        .also { r -> runBlocking { withTimeout(5_000) { r.loaded.first { it } }; r.restore(emptyList(), emptyList()) } }

    private class Source(override val pageCount: Int = 3, val failRender: Boolean = false) : PdfSource {
        @Volatile var closed = false
        override fun pageSize(page: Int) = 400 to 600
        override fun render(page: Int, width: Int): Bitmap {
            if (failRender) throw IllegalStateException("a damaged page")
            return Bitmap.createBitmap(width.coerceAtLeast(1), (width * 1.5f).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        }
        override fun close() { closed = true }
    }

    // ---- a name forged by another device ----

    @Test fun `only a plain file name is a PDF name`() {
        assertEquals("a1b2-17-ab12.pdf", PdfStore.safeName("a1b2-17-ab12.pdf"))
        listOf("", "../library.json", "..", "a/../b.pdf", "pdfs/x.pdf", "..\\x", ".hidden", "x\u0000y.pdf", "/etc/passwd").forEach {
            assertNull(it, PdfStore.safeName(it))
        }
    }

    @Test fun `a forged name points at nothing and removing it deletes nothing outside the folder`() {
        val store = PdfStore(context)
        val outside = File(context.filesDir, "library-like.json").apply { writeText("keep me") }
        store.remove("../library-like.json")
        assertTrue("the file outside the folder is still there", outside.exists())
        assertFalse(store.file("../library-like.json").exists())
        assertEquals(File(context.filesDir, "pdfs"), store.file("../library-like.json").parentFile)
    }

    @Test fun `a song saved with a forged PDF name keeps no PDF, and deleting songs never reaches outside`() {
        val r = repo()
        val outside = File(context.filesDir, "outside.json").apply { writeText("keep me") }
        val id = r.saveSong(Song(title = "Forjada", pdf = "../outside.json", pdfName = "x.pdf"))
        assertEquals("", r.song(id)!!.pdf)
        r.deleteSong(id)
        assertTrue(outside.exists())
    }

    @Test fun `what a band member answers carries no file name`() {
        val link = BandLink(
            context, br.app.stagebook.data.DeviceIdentity(context), br.app.stagebook.data.BandTrust(context),
            br.app.stagebook.data.StageGate(), br.app.stagebook.data.BandInbox(context), repo(),
        )
        val json = """[{"title":"A","author":"B","year":"","text":"[G]linha","pdfFile":"../library.json","pdf":"JVBERi0=","pdfName":"x.pdf"}]"""
        val answered: List<SetlistCloud.SharedSong> = link.readAnswer(json.toByteArray())
        assertEquals(1, answered.size)
        assertEquals("", answered[0].pdfFile)
        assertEquals("", answered[0].pdf)
        assertEquals("", answered[0].pdfName)
    }

    // ---- a PDF closed under the stage, a page that fails ----

    @Test fun `a closed PDF still says its pages and asking it for a page is a cancellation, not a crash`() = runBlocking {
        val source = Source(pageCount = 7)
        val pages = PdfPages(source)
        pages.bitmap(0, 200)
        pages.close()
        assertEquals(7, pages.pageCount)
        var cancelled = false
        try { pages.bitmap(1, 200) } catch (e: CancellationException) { cancelled = true }
        assertTrue(cancelled)
        withTimeout(5_000) { while (!source.closed) kotlinx.coroutines.delay(10) }
    }

    @Test fun `changing the song to another PDF shows the new one and closes the old one, with no crash`() {
        val first = Source(pageCount = 2)
        val second = Source(pageCount = 5)
        var file by mutableStateOf(File("a.pdf"))
        var count = 0
        compose.setContent {
            StagebookTheme {
                val open by rememberPdfOpen(file, open = { f -> if (f.name == "a.pdf") first else second })
                PdfStageView(open, 0, invert = false, onPageCount = { count = it })
            }
        }
        compose.waitUntil(5_000) { count == 2 }
        file = File("b.pdf")
        compose.waitForIdle()
        compose.waitUntil(5_000) { count == 5 }
        compose.waitUntil(5_000) { first.closed }
        assertFalse(second.closed)
    }

    @Test fun `a PDF opened for a song that left meanwhile is closed, not leaked`() {
        val gate = CountDownLatch(1)
        val source = Source()
        var shown by mutableStateOf(true)
        compose.setContent {
            StagebookTheme {
                if (shown) {
                    val open by rememberPdfOpen(File("slow.pdf"), open = { gate.await(5, TimeUnit.SECONDS); source })
                    PdfStageView(open, 0, invert = false, onPageCount = {})
                }
            }
        }
        compose.waitForIdle()
        shown = false   // the song left while its PDF was still opening
        compose.waitForIdle()
        gate.countDown()
        compose.waitUntil(5_000) { source.closed }
    }

    @Test fun `a page that cannot be drawn says so and does not close the app`() {
        var count = 0
        compose.setContent {
            StagebookTheme { PdfStageView(br.app.stagebook.ui.stage.PdfOpen.Ready(PdfPages(Source(failRender = true))), 0, invert = false, onPageCount = { count = it }) }
        }
        compose.waitUntil(5_000) { count > 0 }
        // the count comes before the page fails to draw: wait for the notice itself
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Não consigo abrir este PDF", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Não consigo abrir este PDF", substring = true).assertIsDisplayed()
    }

    @Test fun `a PDF that failed to open gives the stage one page, not the count of the song before`() {
        var count = 7
        compose.setContent { StagebookTheme { PdfStageView(br.app.stagebook.ui.stage.PdfOpen.Failed, 0, invert = false, onPageCount = { count = it }) } }
        compose.waitUntil(5_000) { count == 1 }
    }
}
