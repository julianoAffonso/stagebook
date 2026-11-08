package br.app.stagebook.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** With a headset and a mixer plugged in together, the mixer is the player's (audit 07/11); a headset alone still is. */
class UsbRankTest {
    @Test fun `a device that records too wins over one that only plays, whatever its packet size`() =
        assertTrue(UsbLink.rank(true, 192) > UsbLink.rank(false, 1024))

    @Test fun `with both recording, more channels (a larger packet) win`() =
        assertTrue(UsbLink.rank(true, 384) > UsbLink.rank(true, 192))

    @Test fun `a headset alone is still ranked, so it is picked when nothing better is there`() =
        assertEquals(192L, UsbLink.rank(false, 192))
}
