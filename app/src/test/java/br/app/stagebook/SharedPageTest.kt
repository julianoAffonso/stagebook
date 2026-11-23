package br.app.stagebook

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.ImportDraft
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.MusicBrainz
import br.app.stagebook.data.SharedPages
import br.app.stagebook.data.model.Look
import br.app.stagebook.ui.importer.ImportViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A page shared from a browser's Share opens the import browser on it (user, 22/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SharedPageTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun share(text: String, type: String = "text/plain") = Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_TEXT, text)

    @Test fun `Chrome's title and address give the address`() {
        assertEquals("https://www.cifraclub.com.br/legiao-urbana/tempo-perdido/", SharedPages.pageUrl(share("Tempo Perdido - Legião Urbana\nhttps://www.cifraclub.com.br/legiao-urbana/tempo-perdido/")))
        assertEquals("https://www.cpdl.org/wiki/index.php/Ave_verum", SharedPages.pageUrl(share("https://www.cpdl.org/wiki/index.php/Ave_verum")))
    }

    @Test fun `a text with no address, or not a text, is not a page`() {
        assertNull(SharedPages.pageUrl(share("só uma frase")))
        assertNull(SharedPages.pageUrl(share("https://x.org/a.pdf", type = "application/pdf")))
        assertNull(SharedPages.pageUrl(Intent(Intent.ACTION_VIEW)))
    }

    private fun importer(query: String): ImportViewModel = runBlocking {
        val repo = testRepo()
        repo.updateLook { Look() }
        ImportViewModel(
            repo, br.app.stagebook.data.PageImporter(repo, MusicBrainz(), br.app.stagebook.data.DeezerBpm(), br.app.stagebook.data.Connectivity(context)),
            ImportDraft(), SavedStateHandle(mapOf("query" to query, "kind" to "")), br.app.stagebook.data.ImportQueue(repo),
        )
    }

    @Test fun `a page on a PDF site or a PDF address opens the PDF browser, a chords page doesn't`() {
        assertTrue(importer("https://www.cpdl.org/wiki/index.php/Ave_verum").pdfMode)
        assertTrue(importer("https://example.org/scores/prelude.PDF").pdfMode)
        assertTrue(importer("https://musicabrasilis.org.br/partituras/x").pdfMode)
        assertFalse(importer("https://www.cifraclub.com.br/legiao-urbana/tempo-perdido/").pdfMode)
    }
}
