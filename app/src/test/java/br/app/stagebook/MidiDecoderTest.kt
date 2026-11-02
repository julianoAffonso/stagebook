package br.app.stagebook

import br.app.stagebook.domain.MidiCode
import br.app.stagebook.domain.MidiDecoder
import br.app.stagebook.domain.PedalAction
import br.app.stagebook.domain.PedalLearn
import br.app.stagebook.domain.learnable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test fun `a CC moving to other values without releasing is a repeat`() {
        val d = MidiDecoder("")
        val p = d.feed(bytes(0xB0, 64, 127, 0xB0, 64, 120))
        assertEquals(listOf(0, 1), p.map { it.repeat })
    }

    // Hotone: two buttons on CC 27, one sends 127 and the other 0 (user, 31/10)
    @Test fun `every CC comes with its value, a 0 without a press too`() {
        val p = MidiDecoder("").feed(bytes(0xB0, 27, 0, 0xB0, 27, 127, 0xB0, 27, 0))
        assertEquals(listOf(0, 127, 0), p.map { it.value })
        assertEquals(listOf(false, true, false), p.map { it.down })
    }

    @Test fun `two buttons on the same CC told apart by the value each run their command`() {
        val cc = MidiCode.of(0xB0, 27)
        val keys = mapOf(MidiCode.withValue(cc, 127) to PedalAction.NEXT, MidiCode.withValue(cc, 0) to PedalAction.PREVIOUS)
        val p = MidiDecoder("").feed(bytes(0xB0, 27, 0, 0xB0, 27, 0, 0xB0, 27, 127, 0xB0, 27, 127, 0xB0, 27, 0))
        val run = p.map { PedalAction.hit(it, keys) }.filter { it.runs }.map { it.action }
        assertEquals(listOf(PedalAction.PREVIOUS, PedalAction.PREVIOUS, PedalAction.NEXT, PedalAction.NEXT, PedalAction.PREVIOUS), run)
    }

    @Test fun `a button set by the CC alone still works as before`() {
        val cc = MidiCode.of(0xB0, 27)
        val keys = mapOf(cc to PedalAction.NEXT)
        val p = MidiDecoder("").feed(bytes(0xB0, 27, 127, 0xB0, 27, 0, 0xB0, 27, 127))
        assertEquals(2, p.map { PedalAction.hit(it, keys) }.count { it.runs })
    }

    @Test fun `set up a button records the CC with its value and skips the release right after a press`() {
        val learn = PedalLearn()
        val cc = MidiCode.of(0xB0, 27)
        val d = MidiDecoder("")
        val (down, up) = d.feed(bytes(0xB0, 27, 127, 0xB0, 27, 0))
        assertEquals(MidiCode.withValue(cc, 127), learn.code(down, 1_000))
        assertNull(learn.code(up, 1_200))                                // regular pedal: foot coming off
        assertEquals(MidiCode.withValue(cc, 0), learn.code(up, 5_000))   // Hotone: the 0 button on its own
        assertEquals(0, MidiCode.value(MidiCode.withValue(cc, 0)))
        assertEquals(127, MidiCode.value(MidiCode.withValue(cc, 127)))
        assertNull(MidiCode.value(cc))
        assertEquals(27, MidiCode.number(MidiCode.withValue(cc, 127)))
        assertEquals(MidiCode.Kind.CC, MidiCode.kind(MidiCode.withValue(cc, 0)))
    }

    // the Hotone's 0 button reads as a release: set up a button has to look at it too (device test, 01/11)
    @Test fun `the Hotone CC 0 button reaches set up a button`() {
        val (zero) = MidiDecoder("Hotone").feed(bytes(0xB0, 27, 0))
        assertFalse(zero.down)
        assertTrue(zero.learnable)
        assertEquals(MidiCode.withValue(MidiCode.of(0xB0, 27), 0), PedalLearn().code(zero, 5_000))
        val (noteOff) = MidiDecoder("").feed(bytes(0x90, 60, 100, 0x80, 60, 0)).drop(1)
        assertFalse(noteOff.learnable)
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
