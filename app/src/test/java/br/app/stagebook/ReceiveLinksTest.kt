package br.app.stagebook

import android.content.ClipData
import android.content.ClipboardManager
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.ReceiveLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A receive link opens the receive screen once: tapped, or left in the clipboard by the site (user, 07/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ReceiveLinksTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val links = ReceiveLinks(context)
    private fun copy(text: String) = context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("", text))

    @Test fun `a clip opens only when it is a receive link not opened before`() {
        assertEquals("47-violao-lua", ReceiveLinks.newCode("https://stagebook.app.br/r#47-violao-lua", emptyList()))
        assertNull(ReceiveLinks.newCode("https://stagebook.app.br/r#47-violao-lua", listOf("47-violao-lua")))
        assertNull(ReceiveLinks.newCode("47-violao-lua", emptyList()))   // the words alone: maybe just being typed
        assertNull(ReceiveLinks.newCode("https://example.com", emptyList()))
        assertNull(ReceiveLinks.newCode(null, emptyList()))
    }

    @Test fun `the link the site copied opens once`() {
        copy("https://stagebook.app.br/r#47-violao-lua")
        assertEquals("47-violao-lua", links.fromClipboard())
        assertNull(links.fromClipboard())
    }

    // audit 07/11: a clip the source marks as sensitive (a password manager) is never read, even if it looks like a link
    @Test fun `a clip marked sensitive is not read`() {
        val clip = ClipData.newPlainText("", "https://stagebook.app.br/r#47-violao-lua")
        clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        assertNull(links.fromClipboard())
    }

    @Test fun `other text in the clipboard opens nothing`() {
        copy("Ensaio sábado às 15h")
        assertNull(links.fromClipboard())
    }

    @Test fun `a code sent from here or already tapped doesn't open from the clipboard`() {
        links.markSeen("12-sol-mar")
        copy("https://stagebook.app.br/r#12-sol-mar")
        assertNull(links.fromClipboard())
        assertEquals("33-lua-mar", links.fromIntent("https://stagebook.app.br/r#33-lua-mar"))
        copy("https://stagebook.app.br/r#33-lua-mar")
        assertNull(links.fromClipboard())
    }

    @Test fun `a link that isn't a receive link doesn't open the app on the receive screen`() =
        assertNull(links.fromIntent("https://stagebook.app.br/ajuda"))
}
