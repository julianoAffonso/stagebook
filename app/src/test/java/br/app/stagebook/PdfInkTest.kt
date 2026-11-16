package br.app.stagebook

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import br.app.stagebook.data.model.PdfCrop
import br.app.stagebook.data.model.PdfMark
import br.app.stagebook.data.model.PdfPoint
import br.app.stagebook.ui.stage.PdfInk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The highlighter over a PDF page (audit 16/11): every frame of a zoom, a move or a drawing finger made a path, a list and a
 * line style for each stroke. The kept strokes are made once per size now, and still come out where they did.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfInkTest {
    private val yellow = Color(0xFFFFEB3B)
    private val kept = listOf(
        PdfMark(0, listOf(PdfPoint(0.1f, 0.2f), PdfPoint(0.9f, 0.2f)), 0.04f),   // a line across the top
        PdfMark(0, listOf(PdfPoint(0.5f, 0.8f)), 0.1f),                           // a dot near the bottom
    )

    /** One frame: [ink] drawn on a clear picture of [w] x [h], which comes back to be looked at. */
    private fun frame(ink: PdfInk, live: List<PdfPoint> = emptyList(), w: Int = 200, h: Int = 300): ImageBitmap {
        val image = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat())) { ink.draw(this, live, yellow) }
        return image
    }

    private fun ImageBitmap.inked(x: Int, y: Int) = toPixelMap()[x, y].toArgb() != 0

    @Test fun `the kept strokes are made once and drawn on every frame, and again only for another size`() {
        val ink = PdfInk(kept, null)
        repeat(5) { frame(ink) }
        assertEquals("a frame of a zoom or a move makes nothing again", 1, ink.builds)
        // the finger draws: each event is another frame, and the kept strokes stay as they were made
        val finger = ArrayList<PdfPoint>()
        repeat(20) { i -> finger += PdfPoint(0.1f + i * 0.02f, 0.5f); frame(ink, finger.toList()) }
        assertEquals(1, ink.builds)
        frame(ink, w = 400, h = 600)
        assertEquals("the page on screen changed size: its strokes are laid out again", 2, ink.builds)
    }

    @Test fun `the strokes come out where they were drawn, the one under the finger over them`() {
        val ink = PdfInk(kept, null)
        val still = frame(ink)
        assertEquals(true, still.inked(100, 60))     // the line, y = 0.2 of 300
        assertEquals(true, still.inked(100, 240))    // the dot, y = 0.8 of 300
        assertEquals(false, still.inked(100, 150))   // nothing in the middle yet
        val drawing = frame(ink, listOf(PdfPoint(0.2f, 0.5f), PdfPoint(0.8f, 0.5f)))
        assertEquals(true, drawing.inked(100, 150))
        // the finger went on: the same path is used again, and holds only the stroke as it is now
        val shorter = frame(ink, listOf(PdfPoint(0.2f, 0.5f), PdfPoint(0.3f, 0.5f)))
        assertEquals(false, shorter.inked(150, 150))
        assertEquals(true, shorter.inked(50, 150))
        // one point under the finger is a dot
        assertEquals(true, frame(ink, listOf(PdfPoint(0.5f, 0.5f))).inked(100, 150))
        assertNotEquals(0, still.toPixelMap()[100, 60].toArgb() and 0xFFFFFF)
    }

    @Test fun `with a trim the strokes are placed over the part shown`() {
        // the left half of the page is what shows: a point at a quarter of the page is in the middle of the screen
        val ink = PdfInk(listOf(PdfMark(0, listOf(PdfPoint(0.25f, 0.1f), PdfPoint(0.25f, 0.9f)), 0.02f)), PdfCrop(0, 0f, 0f, 0.5f, 1f))
        val image = frame(ink)
        assertEquals(true, image.inked(100, 150))
        assertEquals(false, image.inked(50, 150))
    }
}
