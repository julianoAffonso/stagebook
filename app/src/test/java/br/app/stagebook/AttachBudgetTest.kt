package br.app.stagebook

import br.app.stagebook.data.SetlistCloud
import br.app.stagebook.domain.BandContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What one receive may download in attachments, whatever the send says (audit 22/11). */
class AttachBudgetTest {
    @Test fun `downloads that fail are paid for too - a send made by hand can't make the device download for ever`() {
        val budget = SetlistCloud.AttachBudget(BandContent.INTERNET_PDF_BYTES)
        var downloads = 0
        // 200 songs, each pointing at a big file that doesn't decrypt
        repeat(200) { i ->
            if (budget.allows("https://ntfy.sh/file/x$i.bin")) { downloads++; budget.charged(null) }
        }
        assertTrue("stopped after $downloads downloads", downloads <= 3)
    }

    @Test fun `the same address is asked for once, and real PDFs cost what they weigh`() {
        val budget = SetlistCloud.AttachBudget(BandContent.INTERNET_PDF_BYTES)
        assertTrue(budget.allows("a"))
        assertEquals(1000, budget.charged(ByteArray(1000))!!.size)
        assertFalse(budget.allows("a"))
        // small PDFs: many fit
        var taken = 0
        repeat(50) { i -> if (budget.allows("b$i")) { budget.charged(ByteArray(100_000)); taken++ } }
        assertEquals(50, taken)
    }
}
