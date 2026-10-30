package br.app.stagebook

import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.QrPayload
import br.app.stagebook.domain.SetlistMatcher
import br.app.stagebook.domain.QrMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetlistMatcherTest {
    private val lib = listOf(
        Song(id = "1", title = "Canção do Mar", author = "Banda A"),
        Song(id = "2", title = "Noite Clara", author = "Banda B"),
        Song(id = "3", title = "Noite Clara", author = "Banda C"),
        Song(id = "4", title = "Estrada Longa", author = "Banda D"),
    )

    @Test fun `título igual sem ligar para acento e caixa`() {
        assertEquals(QrMatch.Found(lib[0]), SetlistMatcher.match(QrPayload.Entry("cancao do mar", ""), lib))
    }

    @Test fun `artista desempata títulos iguais e sem ele o usuário escolhe`() {
        assertEquals(QrMatch.Found(lib[2]), SetlistMatcher.match(QrPayload.Entry("Noite Clara", "Banda C"), lib))
        val m = SetlistMatcher.match(QrPayload.Entry("Noite Clara", ""), lib)
        assertTrue(m is QrMatch.Similar && m.candidates.size == 2)
    }

    @Test fun `parecida vira sugestão e nunca escolha automática - sem nada é falta`() {
        val m = SetlistMatcher.match(QrPayload.Entry("Estrada Longas", ""), lib)
        assertTrue(m is QrMatch.Similar && m.candidates == listOf(lib[3]))
        assertEquals(QrMatch.Missing, SetlistMatcher.match(QrPayload.Entry("Outra Coisa", ""), lib))
    }

    @Test fun `QR do setlist - compactar e ler de volta`() {
        val text = listOf(QrPayload.HEADER, "2", "Show de sábado", "Canção do Mar", "Noite Clara\tBanda C").joinToString("\n")
        val back = QrPayload.unpack(QrPayload.pack(text))
        assertEquals(text, back)
        val parsed = QrPayload.parse(back!!)!!
        assertEquals("Show de sábado", parsed.name)
        assertEquals(listOf(QrPayload.Entry("Canção do Mar", ""), QrPayload.Entry("Noite Clara", "Banda C")), parsed.songs)
    }

    @Test fun `QR puro do formato atual continua valendo e um formato mais novo pede atualização`() {
        val atual = listOf(QrPayload.HEADER, "1", "Show", "A").joinToString("\n")
        assertTrue(QrPayload.parse(atual) != null)
        assertFalse(QrPayload.isNewer(atual))
        assertTrue(QrPayload.isNewer(listOf("LETRAS4", "1", "Show", "A").joinToString("\n")))
        assertFalse(QrPayload.isNewer("LETRAS2\n1\nShow\nA"))
    }

    @Test fun `QR cortado ou de outra coisa não vira setlist`() {
        val packed = QrPayload.pack(listOf(QrPayload.HEADER, "3", "Show", "A", "B", "C").joinToString("\n"))
        assertNull(QrPayload.unpack(packed.copyOf(packed.size - 3)))
        assertNull(QrPayload.unpack("https://exemplo".toByteArray()))
        assertNull(QrPayload.parse(listOf(QrPayload.HEADER, "3", "Show", "A", "B").joinToString("\n")))   // incompleto
    }
}
