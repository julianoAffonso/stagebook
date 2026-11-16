package br.app.stagebook

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
}
