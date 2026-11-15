package br.app.stagebook

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.unit.width
import androidx.compose.ui.test.swipeLeft
import br.app.stagebook.data.PdfPages
import br.app.stagebook.data.PdfSource
import br.app.stagebook.data.model.Look
import br.app.stagebook.data.model.PdfPoint
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
        // the file closes once any page being drawn is done: off the main thread, a moment later
        kotlinx.coroutines.withTimeout(5_000) { while (!source.closed) kotlinx.coroutines.delay(10) }
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

    // ---- zoom ----

    private fun showOpen(source: FakeSource) {
        var count = 0
        compose.setContent { StagebookTheme { PdfStageView(PdfOpen.Ready(PdfPages(source)), 0, invert = false, onPageCount = { count = it }) } }
        compose.waitUntil(5_000) { count > 0 }
        // the count comes before the first bitmap is drawn: wait for the page itself (a busy machine made this flaky)
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Página 1 de 7 do PDF").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    private fun page() = compose.onNodeWithContentDescription("Página 1 de 7 do PDF")

    private fun pinch(out: Boolean) = page().performTouchInput {
        val c = center
        val near = 40f
        val far = 240f
        if (out) pinch(Offset(c.x - near, c.y), Offset(c.x - far, c.y), Offset(c.x + near, c.y), Offset(c.x + far, c.y))
        else pinch(Offset(c.x - far, c.y), Offset(c.x - near, c.y), Offset(c.x + far, c.y), Offset(c.x + near, c.y))
    }

    @Test fun `two fingers apart enlarge the page and together bring it back to its size`() {
        showOpen(FakeSource(7))
        val whole = page().getBoundsInRoot().width
        pinch(out = true)
        compose.waitForIdle()
        val zoomed = page().getBoundsInRoot().width
        assertTrue("$whole -> $zoomed", zoomed > whole * 1.2f)
        pinch(out = false)
        compose.waitForIdle()
        assertEquals(whole.value, page().getBoundsInRoot().width.value, 1f)
    }

    @Test fun `the page never goes past the zoom its pixels allow`() {
        showOpen(FakeSource(7))
        val whole = page().getBoundsInRoot().width
        repeat(4) { pinch(out = true); compose.waitForIdle() }
        val zoomed = page().getBoundsInRoot().width
        assertTrue("$whole -> $zoomed", zoomed <= whole * 4.01f)
    }

    @Test fun `with the page enlarged one finger moves it, at its size it does not`() {
        showOpen(FakeSource(7))
        val left0 = page().getBoundsInRoot().left
        page().performTouchInput { down(center); moveBy(Offset(-120f, 0f)); moveBy(Offset(-120f, 0f)); up() }
        compose.waitForIdle()
        assertEquals("at its size a drag is the stage's: the page stays", left0.value, page().getBoundsInRoot().left.value, 1f)
        pinch(out = true)
        compose.waitForIdle()
        val before = page().getBoundsInRoot().left
        page().performTouchInput { down(center); moveBy(Offset(-120f, 0f)); moveBy(Offset(-120f, 0f)); up() }
        compose.waitForIdle()
        val after = page().getBoundsInRoot().left
        assertTrue("moved: $before -> $after", after < before)
    }

    // ---- highlighter ----

    private fun showMarking(source: FakeSource, marking: Boolean, strokes: MutableList<Pair<Int, List<PdfPoint>>>) {
        var count = 0
        compose.setContent {
            StagebookTheme {
                PdfStageView(
                    PdfOpen.Ready(PdfPages(source)), 0, invert = false, onPageCount = { count = it },
                    marking = marking, onStroke = { page, points -> strokes += page to points },
                )
            }
        }
        compose.waitUntil(5_000) { count > 0 }
        compose.waitForIdle()
    }

    @Test fun `with the highlighter on a finger drawn across the page makes a stroke in the page's own coordinates`() {
        val strokes = mutableListOf<Pair<Int, List<PdfPoint>>>()
        showMarking(FakeSource(7), marking = true, strokes = strokes)
        page().performTouchInput { down(center); moveBy(Offset(-100f, 0f)); moveBy(Offset(-100f, 30f)); up() }
        compose.waitForIdle()
        assertEquals(1, strokes.size)
        val (pageNo, points) = strokes[0]
        assertEquals(0, pageNo)
        assertTrue("points=$points", points.size >= 2 && points.all { it.x in 0f..1f && it.y in 0f..1f })
        assertTrue("it went left across the page", points.last().x < points.first().x)
    }

    @Test fun `without the highlighter a finger makes no stroke`() {
        val strokes = mutableListOf<Pair<Int, List<PdfPoint>>>()
        showMarking(FakeSource(7), marking = false, strokes = strokes)
        page().performTouchInput { down(center); moveBy(Offset(-100f, 0f)); moveBy(Offset(-100f, 30f)); up() }
        compose.waitForIdle()
        assertTrue(strokes.isEmpty())
    }

    @Test fun `with the highlighter on two fingers still zoom and make no stroke`() {
        val strokes = mutableListOf<Pair<Int, List<PdfPoint>>>()
        showMarking(FakeSource(7), marking = true, strokes = strokes)
        val whole = page().getBoundsInRoot().width
        pinch(out = true)
        compose.waitForIdle()
        assertTrue(page().getBoundsInRoot().width > whole * 1.2f)
        assertTrue("a zoom is not a stroke: $strokes", strokes.isEmpty())
    }

    @Test fun `on the stage the highlighter button shows the bar with undo, clear and done, and they act on the page on screen`() {
        val state = StageUiState(false, listOf(StageSong("Partitura", "Ana", "", false, id = "p", pdf = "p.pdf")), look = Look())
        val asked = mutableListOf<String>()
        compose.setContent {
            StagebookTheme {
                StageScreen(
                    state, 0, 0, { _, _ -> }, pdfFile = { java.io.File("/no/such/$it") },
                    onPdfUndo = { id, page -> asked += "undo:$id:$page" }, onPdfClear = { id, page -> asked += "clear:$id:$page" },
                )
            }
        }
        compose.onNodeWithContentDescription("Marca-texto").performClick()
        compose.onNodeWithText("Desfazer").performClick()
        compose.onNodeWithText("Limpar página").performClick()
        assertEquals(listOf("undo:p:0", "clear:p:0"), asked)
        compose.onNodeWithText("Pronto").performClick()
        compose.onAllNodesWithText("Limpar página").assertCountEquals(0)
    }

    @Test fun `a PDF song with a backing track still has its play button on stage`() {
        val state = StageUiState(false, listOf(StageSong("Partitura", "Ana", "", false, id = "p", track = "t.ogg", pdf = "p.pdf")), look = Look(playerMode = true))
        var played = 0
        compose.setContent { StagebookTheme { StageScreen(state, 0, 0, { _, _ -> }, pdfFile = { java.io.File("/no/such/$it") }, onTogglePlay = { played++ }) } }
        compose.onNodeWithContentDescription("Tocar o backing track").performClick()
        assertEquals(1, played)
    }
}
