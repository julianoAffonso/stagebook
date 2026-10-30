package br.app.stagebook

import br.app.stagebook.data.SetlistCloud.SharedSong
import br.app.stagebook.domain.BandContent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BandContentTest {
    private val chart = "{key: G}\n[Verso 1]\n[G]Saio cedo pela [D]rua\n[Em]Onde a estrada [C]vai dar"

    @Test fun `regular lyrics and chords pass`() {
        assertTrue(SharedSong("Canção", "Banda", "1991", chart).allowed())
    }

    @Test fun `long tablature passes`() {
        val tab = "e|" + "-".repeat(150) + "0---3---5" + "-".repeat(150) + "|\nB|" + "-".repeat(300) + "|"
        assertTrue(SharedSong("Solo", text = tab).allowed())
    }

    @Test fun `file hidden in base64 is refused`() {
        val blob = java.util.Base64.getEncoder().encodeToString(ByteArray(600) { it.toByte() })
        assertFalse(SharedSong("Foto", text = "letra\n$blob").allowed())
    }

    @Test fun `control characters and huge text are refused`() {
        assertFalse(SharedSong("Canção", text = "linha\u0000binário").allowed())
        assertFalse(SharedSong("Canção", text = "a ".repeat(BandContent.MAX_TEXT)).allowed())
        assertFalse(SharedSong("", text = chart).allowed())
        assertFalse(SharedSong("T".repeat(BandContent.MAX_TITLE + 1), text = chart).allowed())
    }

    @Test fun `setlist only passes in setlist format`() {
        assertTrue(BandContent.listOk("LETRAS3\n2\nShow\nMúsica A\nMúsica B"))
        assertFalse(BandContent.listOk("qualquer coisa"))
        assertFalse(BandContent.listOk("LETRAS3\n1\nShow\n" + java.util.Base64.getEncoder().encodeToString(ByteArray(600))))
    }
}
