package br.app.stagebook

import br.app.stagebook.data.readCapped
import br.app.stagebook.data.readTextCapped
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What comes from outside is read up to a size, never whole (audit 22/11). */
class CappedReadTest {
    @Test fun `within the limit it is read, over it it is refused`() {
        assertEquals("abc", "abc".byteInputStream().readTextCapped(3))
        assertEquals(100_000, ByteArray(100_000).inputStream().readCapped(100_000).size)
        assertTrue(runCatching { ByteArray(100_001).inputStream().readCapped(100_000) }.exceptionOrNull() is java.io.IOException)
    }
}
