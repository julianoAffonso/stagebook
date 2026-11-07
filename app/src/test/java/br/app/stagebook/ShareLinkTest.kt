package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.domain.ShareCode
import br.app.stagebook.ui.share.codeMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Every internet send goes as the same link (user, 07/11); the 3 words still work typed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class ShareLinkTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun `the link carries the 3 words and comes back as the code`() {
        assertEquals("https://stagebook.app.br/r#47-violao-lua", ShareCode.link("47-violao-lua"))
        assertEquals("47-violao-lua", ShareCode.fromLink(ShareCode.link("47-violao-lua")))
        assertEquals("47-violao-lua", ShareCode.fromLink("stagebook.app.br/r/#47-Violao-LUA"))
        assertEquals("47-violao-lua", ShareCode.fromLink("Ana te chamou: https://www.stagebook.app.br/r#47-violao-lua. Vale por 12 horas."))
    }

    @Test fun `anything else is not a link`() {
        assertNull(ShareCode.fromLink(null))
        assertNull(ShareCode.fromLink("https://stagebook.app.br/ajuda#banda"))
        assertNull(ShareCode.fromLink("https://evilstagebook.app.br/r#47-violao-lua"))
        assertNull(ShareCode.fromLink("https://stagebook.app.br/r#violao-lua"))
        assertNull(ShareCode.fromLink("47-violao-lua"))
    }

    @Test fun `a pasted link works where the 3 words are typed`() =
        assertEquals("47-violao-lua", ShareCode.normalize("https://stagebook.app.br/r#47-violao-lua"))

    @Test fun `the messages have the link and the words`() {
        val link = "https://stagebook.app.br/r#47-violao-lua"
        val set = codeMessage(context.getString(R.string.share_message), context.getString(R.string.share_message_songs), "Show", "47-violao-lua")
        assertTrue(set, set.contains("\"Show\"") && set.contains(link) && set.contains("digite 47-violao-lua"))
        val songs = codeMessage(context.getString(R.string.share_message), context.getString(R.string.share_message_songs), "", "47-violao-lua")
        assertTrue(songs, songs.startsWith("Músicas") && songs.contains(link) && songs.contains("digite 47-violao-lua"))
    }
}
