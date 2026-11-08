package br.app.stagebook

import br.app.stagebook.data.SetlistCloud
import br.app.stagebook.domain.ShareCode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Internet sharing code: easy to type, the key and channel come from it, and only it opens the package. */
class ShareCodeTest {
    // audit 07/11: 60 thousand iterations per call, and the LAN "ok" handler asked once per saved code on every message
    @Test fun `the keys of a code are derived once and the cache stays small`() {
        val first = ShareCode.keys("11-agua-lua")
        assertTrue(first === ShareCode.keys("11-agua-lua"))
        assertNotEquals(first.topic, ShareCode.keys("12-agua-lua").topic)
        (13..40).forEach { ShareCode.keys("$it-agua-lua") }   // more than it keeps
        val again = ShareCode.keys("11-agua-lua")
        assertTrue(first !== again)                            // the oldest was let go...
        assertEquals(first.topic, again.topic)                  // ...and derives to the same thing
        assertArrayEquals(first.key, again.key)
    }

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

    @Test fun `a band package bigger than one song opens with the package cap - not with the song cap`() {
        val mark = byteArrayOf('P'.code.toByte(), '1'.code.toByte())
        val big = (1..40_000).joinToString("\n") { "Linha $it ${it * 7919 % 10007}" }   // ~600 KB, as 30+ songs
        val packed = SetlistCloud.pack(mark, big)
        assertNull(SetlistCloud.unpack(mark, packed))
        assertEquals(big, SetlistCloud.unpack(mark, packed, SetlistCloud.MAX_PACKAGE))
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
