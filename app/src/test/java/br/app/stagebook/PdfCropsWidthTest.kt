package br.app.stagebook

import br.app.stagebook.data.model.PdfCrop
import br.app.stagebook.domain.PdfCrops
import org.junit.Assert.assertEquals
import org.junit.Test

/** The width a page is drawn at so that its trim fills the box: what the next page is prepared with. */
class PdfCropsWidthTest {
    @Test fun `without a trim the page fills the box as far as it fits`() {
        // a 400 x 600 page in a 1000 x 1000 box: height limits, so it is 667 wide
        assertEquals(666, PdfCrops.wholeWidth(400 to 600, null, 1000, 1000))
    }

    @Test fun `with a trim the whole page is drawn wider, by what was cut`() {
        // the right half of the same page (200 x 600) fills the height too: shown 333 wide, so the whole page is twice that
        val half = PdfCrop(PdfCrop.ALL, left = 0.5f)
        assertEquals(666, PdfCrops.wholeWidth(400 to 600, half, 1000, 1000))
        // a narrow box limits by width: the trim (half the page) shown 500 wide, the whole page 1000
        assertEquals(1000, PdfCrops.wholeWidth(400 to 600, half, 500, 5000))
    }

    @Test fun `a small trim never asks for more pixels than a device can take`() {
        // a trim of a tenth of each side would need 100 times the box's pixels: held to the pixel limit
        val tiny = PdfCrop(PdfCrop.ALL, left = 0.9f, top = 0.9f)
        val w = PdfCrops.wholeWidth(400 to 600, tiny, 1200, 1900)
        val h = w.toLong() * 600 / 400
        assertEquals(true, w.toLong() * h <= br.app.stagebook.domain.PdfZoom.MAX_PIXELS)
        // and the zoom's sharper draws are held the same way
        val sharper = br.app.stagebook.domain.PdfZoom.capWidth(w * 4, 400, 600)
        assertEquals(true, sharper.toLong() * (sharper * 600L / 400) <= br.app.stagebook.domain.PdfZoom.MAX_PIXELS)
    }
}
