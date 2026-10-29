package com.juliano.stagebook

import com.juliano.stagebook.data.SetlistCloud.SharedSong
import com.juliano.stagebook.domain.BandContent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BandContentTest {
    private val cifra = "{key: G}\n[Verso 1]\n[G]Saio cedo pela [D]rua\n[Em]Onde a estrada [C]vai dar"

    @Test fun `letra e cifra comuns passam`() {
        assertTrue(SharedSong("Canção", "Banda", "1991", cifra).allowed())
    }

    @Test fun `tablatura longa passa`() {
        val tab = "e|" + "-".repeat(150) + "0---3---5" + "-".repeat(150) + "|\nB|" + "-".repeat(300) + "|"
        assertTrue(SharedSong("Solo", text = tab).allowed())
    }

    @Test fun `arquivo escondido em base64 é recusado`() {
        val blob = java.util.Base64.getEncoder().encodeToString(ByteArray(600) { it.toByte() })
        assertFalse(SharedSong("Foto", text = "letra\n$blob").allowed())
    }

    @Test fun `caracteres de controle e texto gigante são recusados`() {
        assertFalse(SharedSong("Canção", text = "linha\u0000binário").allowed())
        assertFalse(SharedSong("Canção", text = "a ".repeat(BandContent.MAX_TEXT)).allowed())
        assertFalse(SharedSong("", text = cifra).allowed())
        assertFalse(SharedSong("T".repeat(BandContent.MAX_TITLE + 1), text = cifra).allowed())
    }

    @Test fun `setlist só passa no formato de setlist`() {
        assertTrue(BandContent.listOk("LETRAS3\n2\nShow\nMúsica A\nMúsica B"))
        assertFalse(BandContent.listOk("qualquer coisa"))
        assertFalse(BandContent.listOk("LETRAS3\n1\nShow\n" + java.util.Base64.getEncoder().encodeToString(ByteArray(600))))
    }
}
