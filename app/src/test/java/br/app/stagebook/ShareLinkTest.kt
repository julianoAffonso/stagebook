package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.AppLanguage
import br.app.stagebook.data.Languages
import br.app.stagebook.domain.ShareCode
import br.app.stagebook.domain.ShareCode.LinkInfo
import br.app.stagebook.domain.ShareCode.LinkKind
import br.app.stagebook.ui.share.CloudUi
import br.app.stagebook.ui.share.codeMessage
import br.app.stagebook.ui.share.inviteMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // only what no messenger mangles: no "%", "&", "=", "+" or "/" after the "#" (user, 07/11)
    private fun assertSafe(link: String) {
        val fragment = link.substringAfter('#')
        assertTrue(link, fragment.all { (it.isLetterOrDigit() && it.code < 128) || it in "-_." })
        assertEquals(link, 1, fragment.count { it == '.' })
    }

    @Test fun `the link carries the 3 words and comes back as the code`() {
        assertEquals("https://stagebook.app.br/r#47-violao-lua", ShareCode.link("47-violao-lua"))
        assertEquals("47-violao-lua", ShareCode.fromLink(ShareCode.link("47-violao-lua")))
        assertEquals("47-violao-lua", ShareCode.fromLink("stagebook.app.br/r/#47-Violao-LUA"))
        assertEquals("47-violao-lua", ShareCode.fromLink("Ana te chamou: https://www.stagebook.app.br/r#47-violao-lua. Vale por 12 horas."))
    }

    @Test fun `the details go hidden after the code and come back whole`() {
        val link = ShareCode.link("47-violao-lua", LinkKind.SETLIST, "Celular da Ana & Cia", "Show de São João", "pt-BR")
        assertTrue(link, link.startsWith("https://stagebook.app.br/r#47-violao-lua."))
        assertSafe(link)
        assertFalse(link, link.contains("Ana") || link.contains("Show"))   // the names don't show in clear
        assertEquals(LinkInfo("47-violao-lua", LinkKind.SETLIST, "Celular da Ana & Cia", "Show de São João", "pt-BR"), ShareCode.linkInfo(link))
        assertEquals("47-violao-lua", ShareCode.fromLink(link))
        assertEquals("47-violao-lua", ShareCode.fromLink("Toque em $link para receber."))
    }

    @Test fun `accents, spaces, ampersands, emoji and long names round-trip`() {
        val long = "a".repeat(20) + " " + "Ç".repeat(19)
        assertEquals(40, long.length)
        listOf("João Ãngelo", "Ana Lu & Cia 100%", "Baixo 🎸🔥 do Zé", long, "+/=?#\"\\").forEach { name ->
            val link = ShareCode.link("47-violao-lua", LinkKind.INVITE, name, lang = "pt-BR")
            assertSafe(link)
            assertEquals(name, LinkInfo("47-violao-lua", LinkKind.INVITE, name, lang = "pt-BR"), ShareCode.linkInfo(link))
        }
    }

    @Test fun `junk a messenger glues to the end doesn't break the link`() {
        val link = ShareCode.link("47-violao-lua", LinkKind.SONGS, "Ana", lang = "en")
        val info = LinkInfo("47-violao-lua", LinkKind.SONGS, "Ana", lang = "en")
        listOf("$link.", "($link)", "$link,", "Toque: $link. Vale 12 h", "$link-", "${link}_").forEach { assertEquals(it, info, ShareCode.linkInfo(it)) }
        // something rewrote it with the standard alphabet and padding
        val payload = link.substringAfter("lua.")
        val std = java.util.Base64.getEncoder().encodeToString(java.util.Base64.getUrlDecoder().decode(payload))
        assertEquals(info, ShareCode.linkInfo("https://stagebook.app.br/r#47-violao-lua.$std"))
    }

    @Test fun `the older link with parameters still opens and says the same`() {
        val old = "https://stagebook.app.br/r#47-violao-lua&t=l&n=Celular%20da%20Ana%20%26%20Cia&s=Show%20de%20S%C3%A3o%20Jo%C3%A3o&l=pt-BR"
        assertEquals("47-violao-lua", ShareCode.fromLink(old))
        assertEquals(LinkInfo("47-violao-lua", LinkKind.SETLIST, "Celular da Ana & Cia", "Show de São João", "pt-BR"), ShareCode.linkInfo(old))
        assertEquals(LinkInfo("47-violao-lua", LinkKind.INVITE, lang = "en"), ShareCode.linkInfo("https://stagebook.app.br/r#47-violao-lua&t=i&l=en"))
    }

    @Test fun `unreadable details still give the code`() {
        assertEquals(LinkInfo("47-violao-lua"), ShareCode.linkInfo("https://stagebook.app.br/r#47-violao-lua.Vale"))
        assertEquals(LinkInfo("47-violao-lua"), ShareCode.linkInfo("https://stagebook.app.br/r#47-violao-lua.e30"))   // {}
    }

    @Test fun `empty parts are left out and the setlist only goes with a setlist`() {
        assertEquals(LinkInfo("47-violao-lua", LinkKind.INVITE, lang = "en"), ShareCode.linkInfo(ShareCode.link("47-violao-lua", LinkKind.INVITE, "", "Show", "en")))
        val link = ShareCode.link("47-violao-lua", LinkKind.SONGS, "Ana")
        assertEquals("{\"t\":\"m\",\"n\":\"Ana\"}", String(java.util.Base64.getUrlDecoder().decode(link.substringAfter("lua."))))
    }

    @Test fun `anything else is not a link`() {
        assertNull(ShareCode.fromLink(null))
        assertNull(ShareCode.fromLink("https://stagebook.app.br/ajuda#banda"))
        assertNull(ShareCode.fromLink("https://evilstagebook.app.br/r#47-violao-lua"))
        assertNull(ShareCode.fromLink("https://stagebook.app.br/r#violao-lua&t=l"))
        assertNull(ShareCode.fromLink("https://stagebook.app.br/r#violao-lua.e30"))
        assertNull(ShareCode.fromLink("47-violao-lua"))
        assertNull(ShareCode.linkInfo("47-violao-lua"))
    }

    @Test fun `a pasted link works where the 3 words are typed`() {
        assertEquals("47-violao-lua", ShareCode.normalize("https://stagebook.app.br/r#47-violao-lua&t=i&n=Ana"))
        assertEquals("47-violao-lua", ShareCode.normalize(ShareCode.link("47-violao-lua", LinkKind.INVITE, "Ana")))
    }

    @Test fun `the messages have the link and the words`() {
        val set = codeMessage(context.getString(R.string.share_message), context.getString(R.string.share_message_songs),
            CloudUi.Sent("47-violao-lua", "Show, Ensaio", 3, 0, from = "Ana", setlist = "Show"), "pt-BR")
        val setLink = ShareCode.link("47-violao-lua", LinkKind.SETLIST, "Ana", "Show", "pt-BR")
        assertTrue(set, set.contains("\"Show, Ensaio\"") && set.contains(setLink) && set.contains("digite 47-violao-lua"))
        val songs = codeMessage(context.getString(R.string.share_message), context.getString(R.string.share_message_songs),
            CloudUi.Sent("47-violao-lua", "", 3, 0, from = "Ana"), "en")
        assertTrue(songs, songs.startsWith("Músicas") && songs.contains(ShareCode.link("47-violao-lua", LinkKind.SONGS, "Ana", lang = "en")) && songs.contains("digite 47-violao-lua"))
        val invite = inviteMessage(context.getString(R.string.band_invite_message), "Ana", "47-violao-lua", "pt-BR")
        val inviteLink = ShareCode.link("47-violao-lua", LinkKind.INVITE, "Ana", lang = "pt-BR")
        assertTrue(invite, invite.startsWith("Ana te chamou para a banda") && invite.contains(inviteLink) && invite.contains("12 horas"))
        assertEquals(LinkInfo("47-violao-lua", LinkKind.INVITE, "Ana", lang = "pt-BR"), ShareCode.linkInfo(invite))
    }

    @Test fun `the link's language is the one the app shows`() {
        assertEquals("pt-BR", Languages.shownTag(context))   // follows the system (pt-BR here)
        Languages.set(context, AppLanguage.EN)
        assertEquals("en", Languages.shownTag(context))
    }

    @Config(qualifiers = "es")
    @Test fun `a system language without a translation shows English`() = assertEquals("en", Languages.shownTag(context))
}
