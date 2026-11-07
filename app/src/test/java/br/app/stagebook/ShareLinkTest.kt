package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.AppLanguage
import br.app.stagebook.data.Languages
import br.app.stagebook.domain.ShareCode
import br.app.stagebook.domain.ShareCode.LinkKind
import br.app.stagebook.ui.share.CloudUi
import br.app.stagebook.ui.share.codeMessage
import br.app.stagebook.ui.share.inviteMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URLDecoder

/** Every internet send goes as the same link (user, 07/11); the 3 words still work typed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class ShareLinkTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    // the fragment's parameters as the site reads them (decodeURIComponent)
    private fun params(link: String): Map<String, String> = link.substringAfter('#').split('&').drop(1)
        .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('=').replace("+", "%2B"), "UTF-8") }

    @Test fun `the link carries the 3 words and comes back as the code`() {
        assertEquals("https://stagebook.app.br/r#47-violao-lua", ShareCode.link("47-violao-lua"))
        assertEquals("47-violao-lua", ShareCode.fromLink(ShareCode.link("47-violao-lua")))
        assertEquals("47-violao-lua", ShareCode.fromLink("stagebook.app.br/r/#47-Violao-LUA"))
        assertEquals("47-violao-lua", ShareCode.fromLink("Ana te chamou: https://www.stagebook.app.br/r#47-violao-lua. Vale por 12 horas."))
    }

    @Test fun `the link says what was sent - code first, then kind, who, setlist and language`() {
        val link = ShareCode.link("47-violao-lua", LinkKind.SETLIST, "Celular da Ana & Cia", "Show de São João", "pt-BR")
        assertTrue(link, link.startsWith("https://stagebook.app.br/r#47-violao-lua&t=l&n="))
        assertTrue(link, ' ' !in link && link.count { it == '&' } == 4)   // a space or a loose "&" would break the link
        assertEquals(mapOf("t" to "l", "n" to "Celular da Ana & Cia", "s" to "Show de São João", "l" to "pt-BR"), params(link))
        assertEquals("47-violao-lua", ShareCode.fromLink(link))
        assertEquals("47-violao-lua", ShareCode.fromLink("Toque em $link para receber."))
    }

    @Test fun `empty parts are left out and the setlist only goes with a setlist`() {
        assertEquals("https://stagebook.app.br/r#47-violao-lua&t=i&l=en", ShareCode.link("47-violao-lua", LinkKind.INVITE, "", "Show", "en"))
        assertEquals(mapOf("t" to "m", "n" to "Ana"), params(ShareCode.link("47-violao-lua", LinkKind.SONGS, "Ana")))
    }

    @Test fun `anything else is not a link`() {
        assertNull(ShareCode.fromLink(null))
        assertNull(ShareCode.fromLink("https://stagebook.app.br/ajuda#banda"))
        assertNull(ShareCode.fromLink("https://evilstagebook.app.br/r#47-violao-lua"))
        assertNull(ShareCode.fromLink("https://stagebook.app.br/r#violao-lua&t=l"))
        assertNull(ShareCode.fromLink("47-violao-lua"))
    }

    @Test fun `a pasted link works where the 3 words are typed`() =
        assertEquals("47-violao-lua", ShareCode.normalize("https://stagebook.app.br/r#47-violao-lua&t=i&n=Ana"))

    @Test fun `the messages have the link and the words`() {
        val set = codeMessage(context.getString(R.string.share_message), context.getString(R.string.share_message_songs),
            CloudUi.Sent("47-violao-lua", "Show, Ensaio", 3, 0, from = "Ana", setlist = "Show"), "pt-BR")
        assertTrue(set, set.contains("\"Show, Ensaio\"") && set.contains("https://stagebook.app.br/r#47-violao-lua&t=l&n=Ana&s=Show&l=pt-BR") && set.contains("digite 47-violao-lua"))
        val songs = codeMessage(context.getString(R.string.share_message), context.getString(R.string.share_message_songs),
            CloudUi.Sent("47-violao-lua", "", 3, 0, from = "Ana"), "en")
        assertTrue(songs, songs.startsWith("Músicas") && songs.contains("https://stagebook.app.br/r#47-violao-lua&t=m&n=Ana&l=en") && songs.contains("digite 47-violao-lua"))
        val invite = inviteMessage(context.getString(R.string.band_invite_message), "Ana", "47-violao-lua", "pt-BR")
        assertTrue(invite, invite.startsWith("Ana te chamou para a banda") && invite.contains("https://stagebook.app.br/r#47-violao-lua&t=i&n=Ana&l=pt-BR") && invite.contains("12 horas"))
    }

    @Test fun `the link's language is the one the app shows`() {
        assertEquals("pt-BR", Languages.shownTag(context))   // follows the system (pt-BR here)
        Languages.set(context, AppLanguage.EN)
        assertEquals("en", Languages.shownTag(context))
    }

    @Config(qualifiers = "es")
    @Test fun `a system language without a translation shows English`() = assertEquals("en", Languages.shownTag(context))
}
