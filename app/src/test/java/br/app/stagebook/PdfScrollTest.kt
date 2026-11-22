package br.app.stagebook

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertCountEquals
import br.app.stagebook.data.PdfPages
import br.app.stagebook.data.PdfSource
import br.app.stagebook.domain.PdfScroll
import br.app.stagebook.ui.stage.PdfOpen
import br.app.stagebook.ui.stage.PdfScrollView
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A PDF in the stage's scroll mode: the pages one under the other, stepped like the lyrics (user, 22/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class PdfScrollTest {
    @get:Rule val compose = createComposeRule()

    private class FakeSource(override val pageCount: Int) : PdfSource {
        val rendered = mutableSetOf<Int>()
        override fun pageSize(page: Int) = 400 to 600
        override fun render(page: Int, width: Int): Bitmap { rendered += page; return Bitmap.createBitmap(width.coerceAtLeast(1), (width * 1.5f).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888) }
        override fun close() {}
    }

    @Test fun `the steps keep a tenth of the screen and end at the last page's end`() {
        assertEquals(listOf(0, 900, 1800, 2000), PdfScroll.steps(total = 3000, viewport = 1000))
        assertEquals(listOf(0), PdfScroll.steps(total = 800, viewport = 1000))
        // paused at 1234: the next steps go on from there
        assertEquals(listOf(0, 900, 1234, 2000), PdfScroll.steps(total = 3000, viewport = 1000, anchor = 1234).let { listOf(it[0], it[1], it[2], it.last()) })
        assertEquals(listOf(1500, 1500), PdfScroll.heights(listOf(400f to 600f, 200f to 300f), 1000))
        assertEquals(listOf(0, 1500), PdfScroll.tops(listOf(1500, 1500)))
        assertEquals(0..1, PdfScroll.visible(listOf(0, 1500, 3000), listOf(1500, 1500, 1500), 0, 1000))
    }

    @Test fun `the pages are one under the other and a step moves the column up`() {
        val source = FakeSource(4)
        var steps = 0
        var step by mutableIntStateOf(0)
        compose.setContent { StagebookTheme { PdfScrollView(PdfOpen.Ready(PdfPages(source)), step, invert = false, onStepCount = { steps = it }) } }
        compose.waitForIdle()
        // the pictures are drawn off the main thread
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Página 2 de 4 do PDF").fetchSemanticsNodes().isNotEmpty() }
        val first = compose.onNodeWithContentDescription("Página 1 de 4 do PDF").getUnclippedBoundsInRoot()
        val second = compose.onNodeWithContentDescription("Página 2 de 4 do PDF").getUnclippedBoundsInRoot()
        assertEquals("page 2 starts where page 1 ends", first.bottom.value, second.top.value, 1f)
        assertEquals("4 pages of 1849 px on a 2673 px screen: 0, 2405, the end", 3, steps)
        assertTrue("only the pages near the screen are drawn: ${source.rendered}", 3 !in source.rendered)
        step = 1
        compose.waitForIdle()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Página 2 de 4 do PDF").fetchSemanticsNodes().isNotEmpty() }
        val moved = compose.onNodeWithContentDescription("Página 2 de 4 do PDF").getUnclippedBoundsInRoot()
        assertTrue("the column went up: ${second.top} → ${moved.top}", moved.top < second.top)
    }

    // in scroll mode the stage shows the scrolled column, not the page-by-page view (whose hold offers the highlighter)
    @Test fun `in scroll mode a PDF song is the scrolled column`() {
        val state = br.app.stagebook.ui.stage.StageUiState(false, listOf(br.app.stagebook.ui.stage.StageSong("Partitura", "Ana", "", false, id = "p", pdf = "p.pdf")), look = br.app.stagebook.data.model.Look(stageMode = br.app.stagebook.data.model.StageMode.SCROLL))
        compose.setContent { StagebookTheme { br.app.stagebook.ui.stage.StageScreen(state, 0, 0, { _, _ -> }, pdfFile = { java.io.File("/no/such/$it") }) } }
        compose.waitForIdle()
        compose.onRoot().performTouchInput { down(center); advanceEventTime(900); up() }
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("Marca-texto").assertCountEquals(0)
    }
}
