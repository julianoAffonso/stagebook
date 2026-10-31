package br.app.stagebook

import br.app.stagebook.domain.MidiCode
import br.app.stagebook.domain.MidiDecoder
import br.app.stagebook.domain.PedalAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiDecoderTest {
    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    @Test fun `CC presses with 127 and releases with 0`() {
        val d = MidiDecoder("FS-1")
        val p = d.feed(bytes(0xB0, 64, 127, 0xB0, 64, 0))
        assertEquals(2, p.size)
        assertTrue(p[0].down); assertFalse(p[1].down)
        assertEquals(p[0].code, p[1].code)
        assertEquals("FS-1", p[0].device)
        assertEquals(MidiCode.Kind.CC, MidiCode.kind(p[0].code))
        assertEquals(1, MidiCode.channel(p[0].code))
        assertEquals(64, MidiCode.number(p[0].code))
        assertTrue(MidiCode.isMidi(p[0].code))
    }

    @Test fun `running status and clock in between`() {
        val d = MidiDecoder("")
        // CC 80 channel 2, then without repeating the status, with a clock (F8) between the bytes
        val p = d.feed(bytes(0xB1, 80, 0xF8, 127, 80, 0))
        assertEquals(listOf(true, false), p.map { it.down })
        assertEquals(2, MidiCode.channel(p[0].code))
    }

    @Test fun `note with Note Off or zero velocity releases the same key`() {
        val d = MidiDecoder("")
        val p = d.feed(bytes(0x90, 60, 100, 0x80, 60, 0, 0x90, 60, 90, 0x90, 60, 0))
        assertEquals(listOf(true, false, true, false), p.map { it.down })
        assertEquals(1, p.map { it.code }.toSet().size)
        assertEquals(MidiCode.Kind.NOTE, MidiCode.kind(p[0].code))
    }

    @Test fun `program change only presses`() {
        val p = MidiDecoder("").feed(bytes(0xC0, 3))
        assertEquals(1, p.size)
        assertTrue(p[0].down)
        assertEquals(MidiCode.Kind.PROGRAM, MidiCode.kind(p[0].code))
        assertEquals(3, MidiCode.number(p[0].code))
    }

    @Test fun `a CC moving to other values without releasing is a repeat and releasing without pressing is ignored`() {
        val d = MidiDecoder("")
        val p = d.feed(bytes(0xB0, 64, 0, 0xB0, 64, 127, 0xB0, 64, 120))
        assertEquals(listOf(0, 1), p.map { it.repeat })
    }

    // pedals that send only the press: every press counts (user, 31/10: the song moved once and then never again)
    @Test fun `a pedal that sends only 127 presses every time`() {
        val p = MidiDecoder("").feed(bytes(0xB0, 80, 127, 0xB0, 80, 127, 0xB0, 80, 127))
        assertEquals(listOf(0, 0, 0), p.map { it.repeat })
        assertTrue(p.all { it.down })
    }

    @Test fun `a pedal that sends only Note On presses every time`() {
        val p = MidiDecoder("").feed(bytes(0x90, 60, 100, 0x90, 60, 100))
        assertEquals(listOf(0, 0), p.map { it.repeat })
    }

    @Test fun `sysex does not become a button`() {
        assertTrue(MidiDecoder("").feed(bytes(0xF0, 0x41, 0x10, 0x42, 0xF7)).isEmpty())
    }

    @Test fun `MIDI button with no choice does nothing and with a choice runs the command`() {
        val code = MidiCode.of(0xB0, 64)
        assertEquals(PedalAction.NONE, PedalAction.of(code))
        assertEquals(PedalAction.NEXT, PedalAction.of(code, PedalAction.assign(emptyMap(), code, PedalAction.NEXT)))
    }
}
