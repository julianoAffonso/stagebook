package br.app.stagebook

import br.app.stagebook.data.SetlistCloud
import br.app.stagebook.domain.ShareCode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Código de envio pela internet: fácil de digitar, chave e canal saem dele, e só ele abre o pacote. */
class ShareCodeTest {
    @Test fun `new code has a number and two words and typed any way is the same`() {
        val code = ShareCode.generate()
        assertTrue(code.matches(Regex("[1-9][0-9]-[a-z]+-[a-z]+")))
        assertEquals("47-violao-lua", ShareCode.normalize(" 47 Violão LUA "))
        assertEquals("47-violao-lua", ShareCode.normalize("47-violao-lua"))
        assertNull(ShareCode.normalize("violão lua"))
        assertNull(ShareCode.normalize("47-lua"))
        assertEquals(256, ShareCode.wordCount)
    }

    @Test fun `code QR - photo or Scan QR - becomes the code`() {
        assertEquals("47-violao-lua", ShareCode.fromQr(ShareCode.qrText("47-violao-lua")))
        assertNull(ShareCode.fromQr("https://exemplo.com"))
    }

    @Test fun `the same code gives the same key and channel - another code gives others`() {
        val a = ShareCode.keys("47-violao-lua")
        val b = ShareCode.keys("47-violao-lua")
        assertArrayEquals(a.key, b.key)
        assertEquals(a.topic, b.topic)
        assertNotEquals(a.topic, ShareCode.keys("48-violao-lua").topic)
    }

    @Test fun `only the right code opens the package`() {
        val k = ShareCode.keys("47-violao-lua").key
        val msg = ShareCode.encrypt(k, "setlist".toByteArray())
        assertEquals("setlist", String(ShareCode.decrypt(k, msg)!!))
        assertNull(ShareCode.decrypt(ShareCode.keys("12-sol-mar").key, msg))
        assertNull(ShareCode.decrypt(k, "lixo de outra pessoa"))
    }

    @Test fun `song goes and comes back compressed and the ntfy answer is read line by line`() {
        val mark = byteArrayOf('S'.code.toByte(), '1'.code.toByte())
        val text = """{"title":"Canção","text":"[G]linha"}"""
        assertEquals(text, SetlistCloud.unpack(mark, SetlistCloud.pack(mark, text)))
        val lines = listOf(
            """{"id":"a","event":"open"}""",
            """{"id":"b","event":"message","message":"um"}""",
            """não é json""",
            """{"id":"c","event":"message","message":"dois"}""",
        )
        assertEquals(listOf("um", "dois"), SetlistCloud.messages(lines))
    }
}
