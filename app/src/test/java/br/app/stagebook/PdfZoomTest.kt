package br.app.stagebook

import br.app.stagebook.domain.PdfZoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The zoom of a PDF page on the stage: its limits, the pan and the size it is drawn at (user, 15/11). */
class PdfZoomTest {
    @Test fun `a pinch cannot go under 1 nor past the largest zoom`() {
        assertEquals(1f, PdfZoom.scaled(1f, 0.5f, 3f), 0f)
        assertEquals(3f, PdfZoom.scaled(2f, 5f, 3f), 0f)
        assertEquals(2f, PdfZoom.scaled(1f, 2f, 3f), 0f)
    }

    @Test fun `a small page can go to 4 times, a large one only as far as the pixels allow`() {
        assertEquals(PdfZoom.MAX_SCALE, PdfZoom.maxScale(300, 400), 0f)
        val tablet = PdfZoom.maxScale(800, 1200)   // a 10 inch tablet's page
        assertTrue("$tablet", tablet in 2.4f..2.6f)
        assertTrue(PdfZoom.maxScale(3000, 4000) >= 1.5f)   // never under 1.5: a big screen can still zoom a little
    }

    @Test fun `the drawn width is a whole number of times the fitted one`() {
        assertEquals(1, PdfZoom.bucket(1f, 4f))
        assertEquals(2, PdfZoom.bucket(1.2f, 4f))
        assertEquals(2, PdfZoom.bucket(2f, 4f))
        assertEquals(3, PdfZoom.bucket(2.01f, 4f))
        assertEquals(2, PdfZoom.bucket(3.5f, 2.4f))   // past what the pixels allow it is not drawn larger (2 x 2 < 2.4 x 2.4 < 3 x 3)
    }

    @Test fun `a page at its size stays centred and a zoomed one does not leave a gap`() {
        assertEquals(0f to 0f, PdfZoom.clamp(50f to 80f, 1f, 800f, 1200f, 800f, 1200f))
        // twice as large: it can move by half of the extra width on each side
        val (x, y) = PdfZoom.clamp(900f to -900f, 2f, 800f, 1200f, 800f, 1200f)
        assertEquals(400f, x, 0f)
        assertEquals(-600f, y, 0f)
        assertEquals(120f to -30f, PdfZoom.clamp(120f to -30f, 2f, 800f, 1200f, 800f, 1200f))
    }

    @Test fun `a page narrower than the box moves up and down only when it is taller than it`() {
        // 400 wide in a 800 box: no sideways room even at 1.5x (600 wide), but it is taller than the box
        val (x, y) = PdfZoom.clamp(100f to 400f, 1.5f, 400f, 1000f, 800f, 1000f)
        assertEquals(0f, x, 0f)
        assertEquals(250f, y, 0f)
    }
}
