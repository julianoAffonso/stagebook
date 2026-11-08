package br.app.stagebook

import br.app.stagebook.domain.StreamSleep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSleepTest {
    // an open direct USB stream with nothing going on, one input flipped per test
    private fun idle(
        running: Boolean = true, streamOpen: Boolean = true, playing: Boolean = false, clickOn: Boolean = false,
        playWanted: Boolean = false, connecting: Boolean = false, asleep: Boolean = false,
    ) = StreamSleep.idle(running, streamOpen, playing, clickOn, playWanted, connecting, asleep)

    @Test fun `an open stream with nothing sounding is idle`() = assertTrue(idle())

    @Test fun `closes after ten seconds`() = assertEquals(10_000L, StreamSleep.IDLE_MS)

    @Test fun `a backing track playing keeps it open`() = assertFalse(idle(playing = true))

    @Test fun `the metronome click keeps it open`() = assertFalse(idle(clickOn = true))

    @Test fun `a play waiting to start keeps it open`() = assertFalse(idle(playWanted = true))

    @Test fun `a stream that is opening is not idle`() = assertFalse(idle(connecting = true))

    @Test fun `a closed stream is not idle again`() = assertFalse(idle(asleep = true))

    @Test fun `nothing open or no player means nothing to close`() {
        assertFalse(idle(streamOpen = false))
        assertFalse(idle(running = false))
    }

    @Test fun `a stream closed on purpose is not a lost interface`() {
        assertFalse(StreamSleep.interfaceLost(hadUsb = true, linkOpen = false, androidOpen = false, connecting = false, asleep = true))
    }

    @Test fun `an interface that vanished is still lost`() {
        assertTrue(StreamSleep.interfaceLost(hadUsb = true, linkOpen = false, androidOpen = false, connecting = false, asleep = false))
        assertFalse(StreamSleep.interfaceLost(hadUsb = true, linkOpen = true, androidOpen = false, connecting = false, asleep = false))
        assertFalse(StreamSleep.interfaceLost(hadUsb = false, linkOpen = false, androidOpen = false, connecting = false, asleep = false))
    }
}
