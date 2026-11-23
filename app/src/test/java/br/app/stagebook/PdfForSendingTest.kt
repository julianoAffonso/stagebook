package br.app.stagebook

import br.app.stagebook.data.PdfStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The copy of a PDF made to send is made once for a send to several people, and the old ones go (audit 22/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PdfForSendingTest {
    private val context = appContext()

    @Test fun `a PDF that doesn't get smaller is tried once, not for every band member`() {
        val store = PdfStore(context)
        val pdf = File(context.filesDir, "x.pdf").apply { writeBytes("%PDF-1.4\nnot really a pdf".toByteArray()) }
        assertNull(store.forSending(pdf))
        val marks = context.cacheDir.listFiles { f -> f.name.startsWith("send-") }!!
        assertEquals("the try is remembered", 1, marks.size)
        val at = marks[0].lastModified()
        assertNull(store.forSending(pdf))
        assertEquals("asked again, nothing was tried again", at, context.cacheDir.listFiles { f -> f.name.startsWith("send-") }!!.single().lastModified())
    }

    @Test fun `copies left from an hour ago are deleted, a recent one stays`() {
        val store = PdfStore(context)
        val old = File(context.cacheDir, "send-old.pdf").apply { writeText("x"); setLastModified(System.currentTimeMillis() - 2 * PdfStore.SEND_KEEP_MS) }
        val recent = File(context.cacheDir, "send-recent.pdf").apply { writeText("x") }
        store.forSending(File(context.filesDir, "y.pdf").apply { writeBytes("%PDF-1.4\n".toByteArray()) })
        assertFalse(old.exists())
        assertTrue(recent.exists())
    }
}
