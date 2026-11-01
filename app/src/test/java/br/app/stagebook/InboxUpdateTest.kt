package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.BandInbox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The same person sending the same setlist again: identical doesn't duplicate; with changes it replaces and shows
 * "updated".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class InboxUpdateTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun `same does not duplicate - with changes it replaces and marks updated`() {
        val inbox = BandInbox(context)
        inbox.add("ana", "Ana", "LETRAS3\n2\nShow\nA\nB", emptyList())
        inbox.add("ana", "Ana", "LETRAS3\n2\nShow\nA\nB", emptyList())
        assertEquals(1, inbox.items.value.size)
        assertFalse(inbox.items.value.single().updated)
        inbox.add("ana", "Ana", "LETRAS3\n2\nShow\nB\nA", emptyList())
        assertEquals(1, inbox.items.value.size)
        assertTrue(inbox.items.value.single().updated)
        assertTrue(inbox.items.value.single().text.endsWith("B\nA"))
    }

    @Test fun `setlists arriving at the same time are all kept on disk`() {
        val inbox = BandInbox(context)
        val threads = (1..4).map { t ->
            Thread { repeat(6) { i -> inbox.add("p$t", "Pessoa $t", "LETRAS3\n1\nShow $t-$i\nA", emptyList()) } }
        }
        threads.forEach(Thread::start); threads.forEach(Thread::join)
        assertEquals(24, inbox.items.value.size)
        assertEquals(inbox.items.value.map { it.id }.toSet(), BandInbox(context).items.value.map { it.id }.toSet())
    }

    @Test fun `an inbox saved in the prefs by an older version moves to its file`() {
        val old = BandInbox(context).run {
            add("ana", "Ana", "LETRAS3\n1\nShow\nA", emptyList())
            kotlinx.serialization.json.Json.encodeToString(items.value)
        }
        java.io.File(context.filesDir, "inbox.json").delete()
        val prefs = context.getSharedPreferences("inbox", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("items", old).commit()
        assertEquals("Show", BandInbox(context).items.value.single().setName)
        assertTrue(java.io.File(context.filesDir, "inbox.json").exists())
        assertFalse(prefs.contains("items"))
        assertEquals("Show", BandInbox(context).items.value.single().setName)
    }
}
