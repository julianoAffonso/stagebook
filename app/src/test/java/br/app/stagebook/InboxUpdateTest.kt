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
}
