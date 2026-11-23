package br.app.stagebook

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import br.app.stagebook.data.model.PdfCrop
import br.app.stagebook.ui.editor.EditorPdf
import br.app.stagebook.ui.editor.PdfCropDialog
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The trim window while an edge is dragged (audit 16/11): every event of the drag composed the whole window again (the page's
 * picture, the page arrows, the buttons). The frame is read where it is drawn now, so a drag only draws.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class PdfCropDragTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `dragging an edge of the frame composes nothing of the window again, and what was dragged is what is saved`() {
        var composed = 0
        var set: PdfCrop? = null
        compose.setContent {
            StagebookTheme { PdfCropDialog(EditorPdf("a.pdf", pages = 3, bytes = 1_000), onSet = { set = it }, onClear = {}, onComposed = { composed++ }, onDismiss = {}) }
        }
        compose.waitForIdle()
        assertTrue("the window is up", composed > 0)
        val before = composed
        val frame = compose.onNodeWithTag("pdf_crop_frame")
        // the left edge 30% in, in ten steps; then the whole frame a little down and back, and the bottom edge up
        frame.performTouchInput { down(Offset(1f, height / 2f)); repeat(10) { moveBy(Offset(width * 0.03f, 0f)) }; up() }
        compose.waitForIdle()
        frame.performTouchInput { down(Offset(width / 2f, height - 2f)); repeat(10) { moveBy(Offset(0f, -height * 0.02f)) }; up() }
        compose.waitForIdle()
        assertEquals("a drag draws the frame, it does not compose the window", before, composed)
        compose.onNodeWithText("Salvar").performClick()
        compose.waitForIdle()
        val crop = set!!
        assertEquals("left: $crop", 0.3f, crop.left, 0.05f)
        assertEquals("bottom: $crop", 0.8f, crop.bottom, 0.05f)
        assertEquals(0f, crop.top, 0.001f)
        assertEquals(1f, crop.right, 0.001f)
    }

    @Test fun `another page or another scope still composes the window, with the frame of that scope`() {
        var composed = 0
        var set: PdfCrop? = null
        val pdf = EditorPdf("a.pdf", pages = 3, bytes = 1_000, crops = listOf(PdfCrop(PdfCrop.ALL, left = 0.2f), PdfCrop(1, top = 0.4f)))
        compose.setContent { StagebookTheme { PdfCropDialog(pdf, onSet = { set = it }, onClear = {}, onComposed = { composed++ }, onDismiss = {}) } }
        compose.waitForIdle()
        val before = composed
        compose.onNodeWithText("Só esta página").performClick()
        compose.waitForIdle()
        assertTrue(composed > before)
        compose.onNodeWithText("Salvar").performClick()
        compose.waitForIdle()
        // page 1 of 3 has no trim of its own: its frame starts whole, not from the one for all pages
        assertEquals(PdfCrop(0), set)
    }

    // a PDF whose pages are white with a black block: page n has it from (20% + n*5%) to 70% across, 30% to 60% down
    private class BlockPdf(override val pageCount: Int) : br.app.stagebook.data.PdfSource {
        override fun pageSize(page: Int) = 400 to 600
        override fun render(page: Int, width: Int): android.graphics.Bitmap {
            val height = width * 3 / 2
            val pixels = IntArray(width * height) { i ->
                val x = (i % width).toFloat() / width; val y = (i / width).toFloat() / height
                if (x >= 0.2f + page * 0.05f && x < 0.7f && y >= 0.3f && y < 0.6f) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
            return android.graphics.Bitmap.createBitmap(pixels, width, height, android.graphics.Bitmap.Config.ARGB_8888)
        }
        override fun close() {}
    }

    private fun autoCrop(thisPageOnly: Boolean): PdfCrop {
        var set: PdfCrop? = null
        val open = br.app.stagebook.data.PdfPages(BlockPdf(3))
        compose.setContent { StagebookTheme { PdfCropDialog(EditorPdf("a.pdf", pages = 3, bytes = 1_000), onSet = { set = it }, onClear = {}, onDismiss = {}, openPages = open) } }
        compose.waitForIdle()
        if (thisPageOnly) {
            compose.onNodeWithContentDescription("Próxima página").performClick()
            compose.onNodeWithText("Só esta página").performClick()
        }
        compose.onNodeWithText("Automático").performClick()
        // the pages are read off the main thread: the frame arrives a moment later, and the button is free again
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Automático").fetchSemanticsNodes().isNotEmpty() && runCatching { compose.onNodeWithText("Automático").assertIsEnabled() }.isSuccess }
        compose.onNodeWithText("Salvar").performClick()
        compose.waitUntil(5_000) { set != null }
        return set!!
    }

    @Test fun `Automatic puts the frame around what every page has drawn, and Save keeps it for all pages`() {
        val crop = autoCrop(thisPageOnly = false)
        assertEquals(PdfCrop.ALL, crop.page)
        // page 0 starts the furthest left (20%); all end at 70% across, 30% to 60% down; a little air around
        assertEquals("left: $crop", 0.2f - 0.015f, crop.left, 0.01f)
        assertEquals("right: $crop", 0.7f + 0.015f, crop.right, 0.01f)
        assertEquals("top: $crop", 0.3f - 0.015f, crop.top, 0.01f)
        assertEquals("bottom: $crop", 0.6f + 0.015f, crop.bottom, 0.01f)
    }

    @Test fun `for this page only, Automatic reads that page alone`() {
        val crop = autoCrop(thisPageOnly = true)
        assertEquals(1, crop.page)
        assertEquals("left: $crop", 0.25f - 0.015f, crop.left, 0.01f)
    }

    // real text widths (Robolectric's own never wrap), on a phone with large letters
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "+w360dp-h800dp-xxhdpi")
    @Test fun `on a phone with large letters Save and No crop stay on one line each, with Automatic on the title's line`() {
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(3f, fontScale = 1.3f)) {
                StagebookTheme { PdfCropDialog(EditorPdf("a.pdf", pages = 3, bytes = 1_000), onSet = {}, onClear = {}, onDismiss = {}) }
            }
        }
        compose.waitForIdle()
        fun box(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()
        // one line of this text is about 26 dp tall; two would be over 50
        listOf("Salvar", "Sem recorte", "Automático").forEach { assertTrue("$it broke into lines: ${(box(it).bottom - box(it).top).value} dp", (box(it).bottom - box(it).top).value < 40f) }
        assertTrue("Automatic sits on the title's line", box("Automático").top.value < box("Recortar o PDF").bottom.value)
    }
}
