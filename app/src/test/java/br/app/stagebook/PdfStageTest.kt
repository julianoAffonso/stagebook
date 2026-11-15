package br.app.stagebook

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import br.app.stagebook.data.PdfPages
import br.app.stagebook.data.PdfSource
import br.app.stagebook.data.model.Look
import br.app.stagebook.ui.stage.PdfOpen
import br.app.stagebook.ui.stage.PdfStageView
import br.app.stagebook.ui.stage.StageScreen
import br.app.stagebook.ui.stage.StageSong
import br.app.stagebook.ui.stage.StageUiState
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A song that is a PDF on the stage (user, 15/11): its pages in place of the lyrics, turned like any page, at most 3 kept. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class PdfStageTest {
    @get:Rule val compose = createComposeRule()

    /** A PDF of [count] pages, each 400 x 600, drawn as a plain bitmap, that counts what it was asked. */
    private class FakeSource(override val pageCount: Int) : PdfSource {
        val rendered = mutableListOf<Pair<Int, Int>>()
        var closed = false
        override fun pageSize(page: Int) = 400 to 600
        override fun render(page: Int, width: Int): Bitmap {
            rendered += page to width
            return Bitmap.createBitmap(width.coerceAtLeast(1), (width * 1.5f).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        }
        override fun close() { closed = true }
    }

    @Test fun `only three pages are kept and a page seen again is not drawn again`() = runBlocking {
        val source = FakeSource(10)
        val pages = PdfPages(source)
        (0..4).forEach { pages.bitmap(it, 300) }
        assertEquals(3, pages.keptCount)
        assertEquals(5, source.rendered.size)
        pages.bitmap(4, 300)
        assertEquals("page 4 is still kept: not drawn again", 5, source.rendered.size)
        pages.bitmap(0, 300)
        assertEquals("page 0 had gone: drawn again", 6, source.rendered.size)
    }

    @Test fun `closing the pages closes the PDF and keeps nothing`() = runBlocking {
        val source = FakeSource(2)
        val pages = PdfPages(source)
        pages.bitmap(0, 200)
        pages.close()
        assertTrue(source.closed)
        assertEquals(0, pages.keptCount)
    }

    @Test fun `the view tells how many pages there are and draws the one asked for`() {
        val source = FakeSource(7)
        var count = 0
        compose.setContent { StagebookTheme { PdfStageView(PdfOpen.Ready(PdfPages(source)), 3, invert = false, onPageCount = { count = it }) } }
        compose.waitUntil(5_000) { count == 7 }
        compose.onNodeWithContentDescription("Página 4 de 7 do PDF").assertIsDisplayed()
        assertTrue("page 3 was drawn, and 4 ahead", source.rendered.any { it.first == 3 } && source.rendered.any { it.first == 4 })
    }

    @Test fun `a PDF that can't be opened says so`() {
        compose.setContent { StagebookTheme { PdfStageView(PdfOpen.Failed, 0, invert = false, onPageCount = {}) } }
        compose.onNodeWithText("Não consigo abrir este PDF", substring = true).assertExists()
    }

    @Test fun `on the stage a PDF song shows the invert button`() {
        val state = StageUiState(false, listOf(StageSong("Partitura", "Ana", "", false, id = "p", pdf = "p.pdf")), look = Look())
        compose.setContent { StagebookTheme { StageScreen(state, 0, 0, { _, _ -> }, pdfFile = { java.io.File("/no/such/$it") }) } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Inverter as cores do PDF").assertIsDisplayed()
    }

    @Test fun `a lyrics song has no invert button`() {
        val lyrics = StageUiState(false, listOf(StageSong("Letra", "", "linha", false)), look = Look())
        compose.setContent { StagebookTheme { StageScreen(lyrics, 0, 0, { _, _ -> }) } }
        compose.onAllNodesWithContentDescription("Inverter as cores do PDF").assertCountEquals(0)
    }

    @Test fun `the invert button turns the colours over and back`() {
        val state = StageUiState(false, listOf(StageSong("Partitura", "Ana", "", false, id = "p", pdf = "p.pdf")), look = Look())
        var asked: Boolean? = null
        compose.setContent { StagebookTheme { StageScreen(state, 0, 0, { _, _ -> }, pdfFile = { java.io.File("/no/such/$it") }, onPdfInvert = { asked = it }) } }
        compose.onNodeWithContentDescription("Inverter as cores do PDF").performClick()
        assertEquals(true, asked)
    }
}
