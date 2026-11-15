package br.app.stagebook

import br.app.stagebook.data.model.PdfCrop
import br.app.stagebook.data.model.PdfPoint
import br.app.stagebook.domain.PdfCrops
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfCropsTest {
    private val all = PdfCrop(PdfCrop.ALL, left = 0.1f, right = 0.9f)

    @Test fun `a page takes its own trim, else the one for all pages, else none`() {
        val own = PdfCrop(2, top = 0.3f)
        assertEquals(own, PdfCrops.forPage(listOf(all, own), 2))
        assertEquals(all, PdfCrops.forPage(listOf(all, own), 0))
        assertNull(PdfCrops.forPage(emptyList(), 0))
    }

    @Test fun `a whole page is no trim`() {
        assertNull(PdfCrops.forPage(listOf(PdfCrop(0)), 0))
        assertTrue(PdfCrops.set(listOf(all), PdfCrop(PdfCrop.ALL)).isEmpty())   // setting it whole takes the old one off
    }

    @Test fun `a trim is kept inside the page and at least a tenth on each side`() {
        val v = PdfCrops.valid(PdfCrop(0, left = -0.5f, top = 0.95f, right = 0.02f, bottom = 2f))
        assertEquals(0f, v.left, 0f)
        assertEquals(0.9f, v.top, 0.0001f)
        assertEquals(0.1f, v.right, 0.0001f)
        assertEquals(1f, v.bottom, 0f)
    }

    @Test fun `setting one replaces the one for the same page and leaves the others`() {
        val a = PdfCrop(0, left = 0.2f)
        val b = PdfCrop(1, left = 0.3f)
        val out = PdfCrops.set(listOf(a, b), PdfCrop(0, top = 0.4f))
        assertEquals(2, out.size)
        assertEquals(0.4f, out.first { it.page == 0 }.top, 0f)
        assertEquals(b, out.first { it.page == 1 })
        assertEquals(listOf(b), PdfCrops.clear(listOf(a, b), 0))
    }

    @Test fun `a point of the part shown is a point of the whole page`() {
        val crop = PdfCrop(0, left = 0.5f, top = 0.25f, right = 1f, bottom = 0.75f)
        val p = PdfCrops.toPage(crop, PdfPoint(0.5f, 0.5f))
        assertEquals(0.75f, p.x, 0.0001f)
        assertEquals(0.5f, p.y, 0.0001f)
        assertEquals(PdfPoint(0.3f, 0.7f), PdfCrops.toPage(null, PdfPoint(0.3f, 0.7f)))
    }

    @Test fun `an old song without trims opens with none`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val song = json.decodeFromString<br.app.stagebook.data.model.Song>("""{"id":"a","title":"x"}""")
        assertTrue(song.pdfCrops.isEmpty())
    }
}
