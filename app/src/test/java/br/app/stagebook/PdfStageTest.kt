package br.app.stagebook

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.height
import androidx.compose.ui.test.onRoot
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
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Página 4 de 7 do PDF").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Página 4 de 7 do PDF").assertIsDisplayed()
        assertTrue("page 3 was drawn, and 4 ahead", source.rendered.any { it.first == 3 } && source.rendered.any { it.first == 4 })
    }

    @Test fun `a PDF that can't be opened says so`() {
        compose.setContent { StagebookTheme { PdfStageView(PdfOpen.Failed, 0, invert = false, onPageCount = {}) } }
        compose.onNodeWithText("Não consigo abrir este PDF", substring = true).assertExists()
    }

    @Test fun `the stage has no invert button - it lives in the song's editor`() {
        val state = StageUiState(false, listOf(StageSong("Partitura", "Ana", "", false, id = "p", pdf = "p.pdf")), look = Look())
        compose.setContent { StagebookTheme { StageScreen(state, 0, 0, { _, _ -> }, pdfFile = { java.io.File("/no/such/$it") }) } }
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("Inverter as cores do PDF").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Marca-texto").assertCountEquals(0)   // no button either: holding the page offers it
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

    @Test fun `the zoom made is handed back to be kept`() {
        var kept: br.app.stagebook.domain.PdfView? = null
        var count = 0
        compose.setContent {
            StagebookTheme { PdfStageView(PdfOpen.Ready(PdfPages(FakeSource(7))), 0, invert = false, onPageCount = { count = it }, onView = { kept = it }) }
        }
        compose.waitUntil(5_000) { count > 0 }
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Página 1 de 7 do PDF").fetchSemanticsNodes().isNotEmpty() }
        pinch(out = true)
        compose.waitForIdle()
        assertTrue("the zoom was handed back: $kept", (kept?.scale ?: 1f) > 1.2f)
    }

    @Test fun `a page opens with the zoom saved for the song`() {
        var count = 0
        compose.setContent {
            StagebookTheme { PdfStageView(PdfOpen.Ready(PdfPages(FakeSource(7))), 0, invert = false, onPageCount = { count = it }, view = br.app.stagebook.domain.PdfView(2f, 0f, 0f)) }
        }
        compose.waitUntil(5_000) { count > 0 }
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Página 1 de 7 do PDF").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        val box = compose.onRoot().getBoundsInRoot().width
        assertTrue("enlarged past the box: ${page().getBoundsInRoot().width} in $box", page().getBoundsInRoot().width > box * 1.2f)
    }

    private fun showCropped(crops: List<br.app.stagebook.data.model.PdfCrop>, pageAt: Int = 0): FakeSource {
        val source = FakeSource(3)
        var count = 0
        compose.setContent { StagebookTheme { PdfStageView(PdfOpen.Ready(PdfPages(source)), pageAt, invert = false, onPageCount = { count = it }, crops = crops) } }
        compose.waitUntil(5_000) { count > 0 }
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Página ${pageAt + 1} de 3 do PDF").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        return source
    }

    @Test fun `a trimmed page shows only its part, in that part's shape and as large as the box allows`() {
        // the page is 400 x 600 (2:3); the right half of it is 200 x 600 (1:3), twice as tall as wide
        showCropped(listOf(br.app.stagebook.data.model.PdfCrop(page = br.app.stagebook.data.model.PdfCrop.ALL, left = 0.5f)))
        val b = compose.onNodeWithContentDescription("Página 1 de 3 do PDF").getBoundsInRoot()
        assertEquals("shaped as the trim (1:3): ${b.width} x ${b.height}", 3f, b.height.value / b.width.value, 0.05f)
    }

    @Test fun `a page with its own trim takes it over the one for all pages, and one with none shows whole`() {
        val crops = listOf(
            br.app.stagebook.data.model.PdfCrop(page = br.app.stagebook.data.model.PdfCrop.ALL, left = 0.5f),
            br.app.stagebook.data.model.PdfCrop(page = 1, top = 0.5f),   // the lower half: 400 x 300, wider than tall
        )
        showCropped(crops, pageAt = 1)
        val b = compose.onNodeWithContentDescription("Página 2 de 3 do PDF").getBoundsInRoot()
        assertEquals("shaped as its own trim (4:3): ${b.width} x ${b.height}", 4f / 3f, b.width.value / b.height.value, 0.05f)
    }

    @Test fun `a trim only draws the page wide enough for its part to fill the box`() {
        val source = showCropped(listOf(br.app.stagebook.data.model.PdfCrop(page = br.app.stagebook.data.model.PdfCrop.ALL, left = 0.5f)))
        val first = source.rendered.first { it.first == 0 }.second
        // half the width is shown, so the whole page is drawn twice as wide as the part on screen
        val box = compose.onNodeWithContentDescription("Página 1 de 3 do PDF").getBoundsInRoot()
        val shownPx = box.width.value * 1f   // mdpi in these tests: dp = px
        assertTrue("whole page $first px for a part of $shownPx px", first >= shownPx * 1.9f)
    }

    @Test fun `the view is kept only when a gesture changed the zoom or the place, not on a tap`() {
        val kept = mutableListOf<br.app.stagebook.domain.PdfView>()
        var count = 0
        compose.setContent {
            StagebookTheme { PdfStageView(PdfOpen.Ready(PdfPages(FakeSource(7))), 0, invert = false, onPageCount = { count = it }, onView = { kept += it }) }
        }
        compose.waitUntil(5_000) { count > 0 }
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Página 1 de 7 do PDF").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        page().performTouchInput { down(center); up() }   // a tap: it turns the page, it does not touch the view
        compose.waitForIdle()
        assertEquals(0, kept.size)
        pinch(out = true)
        compose.waitForIdle()
        assertTrue("the zoom was kept", kept.isNotEmpty() && kept.last().scale > 1.2f)
    }

    @Test fun `the page never goes past the zoom its pixels allow`() {
        showOpen(FakeSource(7))
        val whole = page().getBoundsInRoot().width
        repeat(4) { pinch(out = true); compose.waitForIdle() }
        val zoomed = page().getBoundsInRoot().width
        assertTrue("$whole -> $zoomed", zoomed <= whole * 4.01f)
    }

    @Test fun `with the page enlarged one finger does not move it, two fingers do`() {
        showOpen(FakeSource(7))
        pinch(out = true)
        compose.waitForIdle()
        val before = page().getBoundsInRoot().left
        // a scroll with one finger is the stage's (the page stays): that is what made it walk by accident
        page().performTouchInput { down(center); moveBy(Offset(-120f, 0f)); moveBy(Offset(-120f, 0f)); up() }
        compose.waitForIdle()
        assertEquals("one finger: the page stays", before.value, page().getBoundsInRoot().left.value, 0.5f)
        // two fingers together, moving left: the page goes with them
        page().performTouchInput {
            val c = center
            down(0, Offset(c.x - 20f, c.y)); down(1, Offset(c.x + 20f, c.y))
            // both fingers in one event, so the distance between them (the zoom) does not change on the way
            repeat(2) { updatePointerBy(0, Offset(-120f, 0f)); updatePointerBy(1, Offset(-120f, 0f)); move() }
            up(0); up(1)
        }
        compose.waitForIdle()
        assertTrue("two fingers moved it: $before -> ${page().getBoundsInRoot().left}", page().getBoundsInRoot().left < before)
    }

    @Test fun `a touch with a small wobble on an enlarged page does not move it`() {
        showOpen(FakeSource(7))
        pinch(out = true)
        compose.waitForIdle()
        val before = page().getBoundsInRoot().left
        // 14 px at xxhdpi is well under a thumb's width: a tap by accident
        page().performTouchInput { down(center); moveBy(Offset(-14f, 0f)); up() }
        compose.waitForIdle()
        assertEquals("the page stays", before.value, page().getBoundsInRoot().left.value, 0.5f)
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
        // the count comes before the first bitmap: wait for the page itself
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Página 1 de 7 do PDF").fetchSemanticsNodes().isNotEmpty() }
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

    @Test fun `holding a PDF page offers the highlighter, and with marks undo and clear page, which act on the page on screen`() {
        val marks = listOf(br.app.stagebook.data.model.PdfMark(0, listOf(PdfPoint(0.1f, 0.1f), PdfPoint(0.5f, 0.1f)), 0.025f))
        val state = StageUiState(false, listOf(StageSong("Partitura", "Ana", "", false, id = "p", pdf = "p.pdf", pdfMarks = marks)), look = Look())
        val asked = mutableListOf<String>()
        compose.setContent {
            StagebookTheme {
                StageScreen(
                    state, 0, 0, { _, _ -> }, pdfFile = { java.io.File("/no/such/$it") },
                    onPdfUndo = { id, page -> asked += "undo:$id:$page" }, onPdfClear = { id, page -> asked += "clear:$id:$page" },
                )
            }
        }
        compose.waitForIdle()
        fun hold() = compose.onRoot().performTouchInput { down(center); advanceEventTime(900); up() }
        hold(); compose.waitForIdle()
        compose.onNodeWithContentDescription("Desfazer").performClick()
        hold(); compose.waitForIdle()
        compose.onNodeWithContentDescription("Limpar página").performClick()
        assertEquals(listOf("undo:p:0", "clear:p:0"), asked)
    }

    @Test fun `the highlighter from the hold shows the same bar as the lyrics' and Done leaves it`() {
        val state = StageUiState(false, listOf(StageSong("Partitura", "Ana", "", false, id = "p", pdf = "p.pdf")), look = Look())
        compose.setContent { StagebookTheme { StageScreen(state, 0, 0, { _, _ -> }, pdfFile = { java.io.File("/no/such/$it") }) } }
        compose.waitForIdle()
        compose.onRoot().performTouchInput { down(center); advanceEventTime(900); up() }
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("Desfazer").assertCountEquals(0)   // nothing marked on the page: only the highlighter is offered
        compose.onNodeWithContentDescription("Marca-texto").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Pronto").performClick()
        compose.onAllNodesWithText("Pronto").assertCountEquals(0)
    }

    @Test fun `a PDF song with a backing track still has its play button on stage`() {
        val state = StageUiState(false, listOf(StageSong("Partitura", "Ana", "", false, id = "p", track = "t.ogg", pdf = "p.pdf")), look = Look(playerMode = true))
        var played = 0
        compose.setContent { StagebookTheme { StageScreen(state, 0, 0, { _, _ -> }, pdfFile = { java.io.File("/no/such/$it") }, onTogglePlay = { played++ }) } }
        compose.onNodeWithContentDescription("Tocar o backing track").performClick()
        assertEquals(1, played)
    }
}
