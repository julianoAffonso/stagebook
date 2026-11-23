package br.app.stagebook

import br.app.stagebook.domain.PdfAutoCrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The automatic trim of a PDF page (user, 23/11): the frame around what is drawn, without the blank margins. */
class PdfAutoCropTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    // a white page of [w] x [h] with a black block from (x0, y0) to (x1, y1), ends excluded
    private fun page(w: Int, h: Int, x0: Int, y0: Int, x1: Int, y1: Int, paper: Int = white) =
        IntArray(w * h) { i -> if (i % w in x0 until x1 && i / w in y0 until y1) black else paper }

    @Test fun `the frame holds what is drawn, with a little air around it`() {
        val f = PdfAutoCrop.frame(page(200, 400, 40, 100, 160, 300), 200, 400)!!
        assertEquals(0.2f - PdfAutoCrop.AIR, f.left, 0.001f)
        assertEquals(0.25f - PdfAutoCrop.AIR, f.top, 0.001f)
        assertEquals(0.8f + PdfAutoCrop.AIR, f.right, 0.001f)
        assertEquals(0.75f + PdfAutoCrop.AIR, f.bottom, 0.001f)
    }

    @Test fun `a page drawn to its edges keeps the whole page, never past it`() {
        val f = PdfAutoCrop.frame(page(100, 100, 0, 0, 100, 100), 100, 100)!!
        assertEquals(PdfAutoCrop.Frame(0f, 0f, 1f, 1f), f)
    }

    @Test fun `a blank page has no frame, and a clear one is paper too`() {
        assertNull(PdfAutoCrop.frame(IntArray(100 * 100) { white }, 100, 100))
        assertNull(PdfAutoCrop.frame(IntArray(100 * 100), 100, 100))
    }

    @Test fun `a scan's grey haze and a speck of dust are not content`() {
        val haze = 0xFFE6E6E6.toInt()
        val pixels = page(500, 500, 100, 100, 400, 400, paper = haze)
        pixels[5 * 500 + 5] = black   // one dark dot in the margin
        val f = PdfAutoCrop.frame(pixels, 500, 500)!!
        assertEquals(0.2f - PdfAutoCrop.AIR, f.left, 0.001f)
        assertEquals(0.2f - PdfAutoCrop.AIR, f.top, 0.001f)
    }

    @Test fun `one frame for several pages holds what each has, and blank pages don't count`() {
        val a = PdfAutoCrop.Frame(0.2f, 0.1f, 0.8f, 0.6f)
        val b = PdfAutoCrop.Frame(0.1f, 0.3f, 0.7f, 0.9f)
        assertEquals(PdfAutoCrop.Frame(0.1f, 0.1f, 0.8f, 0.9f), PdfAutoCrop.around(listOf(a, null, b)))
        assertNull(PdfAutoCrop.around(listOf(null, null)))
    }

    @Test fun `a short PDF is read whole, a long one at pages spread from the first to the last`() {
        assertEquals(listOf(0, 1, 2), PdfAutoCrop.sample(3))
        val long = PdfAutoCrop.sample(300, most = 24)
        assertEquals(24, long.size)
        assertEquals(0, long.first())
        assertEquals(299, long.last())
    }
}
