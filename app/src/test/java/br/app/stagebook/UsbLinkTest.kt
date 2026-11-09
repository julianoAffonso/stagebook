package br.app.stagebook.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What each kind of USB audio interface tells about itself, read the way the engine needs it. */
class UsbLinkTest {
    private fun bytes(hex: String) = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    // the descriptors of a real USB Audio 1.0 card: stereo, 16 bits, only 44.1 kHz, adaptive output
    private val nanoStyle = bytes("12010002ef020140c333011301000102030109022e010501008032080b0003010100040904000000010100000a2401000148000201020c24020101020002030000000a2406020101030000000924030301010002000c24020401010002030000000a240605040103000000092403060103000500090401000001020000090401010101020000072401040101000b2402010202100144ac0009050209b80001000007250101000000090402000001020000090402010101020000072401030101000b2402010202100144ac0009058105b80001000007250101000000080b03020100000509040300000101000009240100010900010409040400020103000007240100012f0006240201010006240202020009240301030102010009240302040101010009050302400000000005250101010905830240000000000525010103")

    @Test fun `a USB Audio 1 card gives its own format and rate`() {
        val link = UsbLink().apply { scan(nanoStyle) }
        assertTrue(link.classOne)
        assertEquals(0x02, link.ep)
        assertEquals(2, link.channels)
        assertEquals(2, link.subslot)
        assertEquals(16, link.bitRes)
        assertEquals(44100, link.rate)
        assertEquals(184, link.maxPacket)
    }

    @Test fun `an adaptive output doesn't open the input`() {
        assertEquals(-1, UsbLink().apply { scan(nanoStyle) }.inEp)
    }

    // the shape of a USB Audio 2.0 mixer: channels in the general block, size in a short format block
    @Test fun `a USB Audio 2 interface stays at 48 kHz`() {
        val classTwo = bytes(
            "0904010001010220" + "00" + "1024" + "01" + "0100" + "0100000000" + "04" + "00000000" + "00" +
                "0624" + "02" + "01" + "04" + "18" + "07" + "05" + "01" + "05" + "6000" + "01",
        )
        val link = UsbLink().apply { scan(classTwo) }
        assertFalse(link.classOne)
        assertEquals(48000, link.rate)
        assertEquals(4, link.subslot)
    }

    // an output and an input, both USB Audio 2.0 (the shape of a 4-in/4-out interface)
    private val withInput = bytes(
        "0904010001010220" + "00" + "1024" + "01" + "0100" + "0100000000" + "04" + "00000000" + "00" +
            "0624" + "02" + "01" + "04" + "18" + "07" + "05" + "01" + "05" + "9001" + "01" +
            "0904020101010220" + "00" + "1024" + "01" + "0100" + "0100000000" + "04" + "00000000" + "00" +
            "0624" + "02" + "01" + "04" + "18" + "07" + "05" + "82" + "05" + "9001" + "01",
    )

    @Test fun `an interface with an input opens it as the clock`() {
        val link = UsbLink().apply { scan(withInput) }
        assertEquals(0x01, link.ep)
        assertEquals(0x82, link.inEp)
        assertEquals(2, link.inIfNum)
    }

    // on a device where reading that input stopped the output, it isn't opened at all (S26, 09/11)
    @Test fun `an input remembered as stopping the output is left closed`() {
        val link = UsbLink().apply { noInput = true; scan(withInput) }
        assertEquals(0x01, link.ep)
        assertEquals(-1, link.inEp)
        assertEquals(-1, link.inIfNum)
    }
}
