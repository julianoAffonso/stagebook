package br.app.stagebook

import br.app.stagebook.data.model.Library
import br.app.stagebook.data.model.Setlist
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.BandContent
import br.app.stagebook.domain.QrPayload
import br.app.stagebook.domain.SetlistBreaks
import br.app.stagebook.domain.SharedBreaks
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * A setlist's breaks after its package (band, 3 words): they come back, an app that doesn't know them still reads the
 * setlist, and whatever another device puts there is checked before it counts.
 */
class SharedBreaksTest {
    private val songs = listOf("A", "B", "C", "D", "E").mapIndexed { i, t -> Song(id = "s$i", title = t, author = "x") }
    private fun library(breaks: List<Int>) = Library(songs = songs, sets = listOf(Setlist("set", "Show", songs.map { it.id }, breaks)))
    private fun qr(breaks: List<Int> = emptyList()) = library(breaks).let { QrPayload.build(it.sets[0], it) }
    private val text = QrPayload.unpack(qr())!!

    private fun tail(s: String) = qr() + s.toByteArray(Charsets.US_ASCII)

    /**
     * How the app read a setlist's package before the breaks went along, copied as it was (QrPayload.unpack at
     * v0.1.x): the proof that an older app gets the setlist from the new package.
     */
    private fun oldUnpack(bytes: ByteArray?): String? {
        if (bytes == null || bytes.size <= 2 || bytes[0] != 'Q'.code.toByte() || bytes[1] !in '1'.code.toByte()..'9'.code.toByte()) return null
        val inflater = Inflater(true)
        return try {
            inflater.setInput(bytes, 2, bytes.size - 2)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) return null
                out.write(buf, 0, n)
                if (out.size() > 64 * 1024) return null
            }
            out.toString(Charsets.UTF_8.name())
        } catch (_: DataFormatException) {
            null
        } finally {
            inflater.end()
        }
    }

    @Test fun `the breaks come back as they went`() {
        assertEquals(listOf(2, 4), SharedBreaks.read(SharedBreaks.pack(qr(), listOf(2, 4)), text))
        assertEquals(emptyList<Int>(), SharedBreaks.read(SharedBreaks.pack(qr(), emptyList()), text))
    }

    @Test fun `an older app reads the same setlist from the package with breaks, and sees nothing newer`() {
        val packed = SharedBreaks.pack(qr(), listOf(2, 4))
        assertEquals(text, oldUnpack(packed))
        assertEquals(text, QrPayload.unpack(packed))
        assertTrue(BandContent.listOk(oldUnpack(packed)!!))
        assertEquals(5, QrPayload.parse(oldUnpack(packed)!!)!!.songs.size)
        assertFalse(QrPayload.isNewer(oldUnpack(packed)!!))
    }

    @Test fun `a package from an older app, or the QR, says nothing about breaks`() {
        assertNull(SharedBreaks.read(qr(), text))
    }

    @Test fun `the QR of a setlist is the same with and without breaks`() {
        assertArrayEquals(qr(), qr(listOf(2, 4)))
    }

    @Test fun `positions out of place are left out, repeated ones count once and they come out in order`() {
        assertEquals(listOf(2, 3), SharedBreaks.read(tail("B1:7,3,3,0,99,2,5"), text))
        // a number no setlist has is no position
        assertEquals(listOf(2), SharedBreaks.read(tail("B1:99999999999999999999,2"), text))
        assertEquals(listOf(1), SharedBreaks.read(tail("B1:,,1,"), text))
        assertEquals(SetlistBreaks.normalise(listOf(1, 2, 3, 4), 5), SharedBreaks.read(tail("B1:" + (0..300).joinToString(",")), text))
    }

    @Test fun `a tail that isn't the breaks is taken as none sent, never an error`() {
        for (odd in listOf("B1", "B", "B2:1,2", "B1:1;2", "B1:-1", "B1:1 2", "b1:1", "B1:2\n", "\u0000\u0001\u0002", "B1:" + "1,".repeat(2_000))) {
            assertNull(odd.take(12), SharedBreaks.read(tail(odd), text))
        }
    }

    @Test fun `bytes that aren't a setlist's package give nothing`() {
        assertNull(SharedBreaks.read(ByteArray(0), text))
        assertNull(SharedBreaks.read(byteArrayOf(1, 2), text))
        assertNull(SharedBreaks.read(ByteArray(300) { it.toByte() }, text))
        assertNull(SharedBreaks.read(qr().copyOf(qr().size - 3), text))   // cut short
        assertNull(SharedBreaks.read(SharedBreaks.pack(qr(), listOf(2)), "not a setlist"))
    }

    @Test fun `a received setlist keeps each break before the same song, or the next one that is here`() {
        // A B | C D | E, and the receiver lacks C: the break stays before D
        assertEquals(SetlistBreaks.Placed(listOf("a", "b", "d", "e"), listOf(2, 3)), SetlistBreaks.place(listOf("a", "b", null, "d", "e"), listOf(2, 4)))
        // lacking E: the last break has no song after it and goes away
        assertEquals(SetlistBreaks.Placed(listOf("a", "b", "c", "d"), listOf(2)), SetlistBreaks.place(listOf("a", "b", "c", "d", null), listOf(2, 4)))
        // lacking A and B: the first break would be at the start and goes away
        assertEquals(SetlistBreaks.Placed(listOf("c", "d", "e"), listOf(2)), SetlistBreaks.place(listOf(null, null, "c", "d", "e"), listOf(2, 4)))
        // lacking C and D: the two breaks meet before E and become one
        assertEquals(SetlistBreaks.Placed(listOf("a", "b", "e"), listOf(2)), SetlistBreaks.place(listOf("a", "b", null, null, "e"), listOf(2, 4)))
        // the same song chosen for two rows counts once
        assertEquals(SetlistBreaks.Placed(listOf("a", "b", "e"), listOf(2)), SetlistBreaks.place(listOf("a", "b", "a", "b", "e"), listOf(2, 4)))
        assertEquals(SetlistBreaks.Placed(emptyList(), emptyList()), SetlistBreaks.place(listOf(null, null), listOf(1)))
        assertEquals(SetlistBreaks.Placed(listOf("a", "b"), emptyList()), SetlistBreaks.place(listOf("a", "b"), listOf(-3, 0, 2, 50)))
    }
}
