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
}
