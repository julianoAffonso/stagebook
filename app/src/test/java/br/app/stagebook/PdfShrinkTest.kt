package br.app.stagebook

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.PdfShrink
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** A big PDF is made smaller on attach, and only when it pays (user, 22/11). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class PdfShrinkTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    // a "scan": a big photo-like picture stored without loss, as a scanner's PDF often is
    private fun scannedPdf(side: Int, pages: Int = 1): File {
        PDFBoxResourceLoader.init(context)
        val f = File(context.cacheDir, "scan-$side-$pages.pdf")
        PDDocument().use { doc ->
            val bmp = Bitmap.createBitmap(side, (side * 1.4f).toInt(), Bitmap.Config.RGB_565)   // no alpha, as a scan (a picture with transparency is left alone)
            val rnd = java.util.Random(7)
            for (y in 0 until bmp.height step 2) for (x in 0 until bmp.width step 2) {
                val g = 200 + rnd.nextInt(56)
                bmp.setPixel(x, y, android.graphics.Color.rgb(g, g, g))
            }
            val img = LosslessFactory.createFromImage(doc, bmp)   // one picture, on every page
            repeat(pages) {
                val page = PDPage()
                doc.addPage(page)
                PDPageContentStream(doc, page).use { it.drawImage(img, 0f, 0f, page.mediaBox.width, page.mediaBox.height) }
            }
            doc.save(f)
        }
        return f
    }

    @Test fun `a big scanned PDF comes out much smaller and still a PDF`() {
        val big = scannedPdf(2600)
        assertTrue("the sample is big: ${big.length()}", big.length() >= PdfShrink.MIN_BYTES)
        val small = PdfShrink.shrunk(context, big)
        assertNotNull(small)
        assertTrue("${small!!.length()} vs ${big.length()}", small.length() <= big.length() * PdfShrink.KEEP_RATIO)
        assertTrue(small.readBytes().copyOfRange(0, 5).decodeToString() == "%PDF-")
    }

    @Test fun `a small PDF or a file that is no PDF is left as it is`() {
        val small = scannedPdf(300)
        assertNull(PdfShrink.shrunk(context, small))
        val junk = File(context.cacheDir, "junk.pdf").apply { writeBytes(ByteArray((PdfShrink.MIN_BYTES + 10).toInt())) }
        assertNull(PdfShrink.shrunk(context, junk))
        assertTrue(junk.exists())
    }

    // audit 22/11: each page got its own copy of the picture, and the "smaller" file weighed four of them
    @Test fun `a picture used on several pages is made smaller once, not once per page`() {
        val one = PdfShrink.shrunk(context, scannedPdf(2600), minBytes = 0)!!.length()
        val four = PdfShrink.shrunk(context, scannedPdf(2600, pages = 4), minBytes = 0)!!.length()
        assertTrue("four pages of the same picture: $four, one page: $one", four < one * 2)
    }
}
