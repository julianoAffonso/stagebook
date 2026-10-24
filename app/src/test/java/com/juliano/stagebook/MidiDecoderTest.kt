package com.juliano.stagebook

import com.juliano.stagebook.domain.MidiCode
import com.juliano.stagebook.domain.MidiDecoder
import com.juliano.stagebook.domain.PedalAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiDecoderTest {
    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    @Test fun `CC aperta com 127 e solta com 0`() {
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

    @Test fun `running status e clock no meio`() {
        val d = MidiDecoder("")
        // CC 80 canal 2, depois sem repetir o status, com um clock (F8) entre os bytes
        val p = d.feed(bytes(0xB1, 80, 0xF8, 127, 80, 0))
        assertEquals(listOf(true, false), p.map { it.down })
        assertEquals(2, MidiCode.channel(p[0].code))
    }

    @Test fun `nota com Note Off ou velocidade zero solta a mesma tecla`() {
        val d = MidiDecoder("")
        val p = d.feed(bytes(0x90, 60, 100, 0x80, 60, 0, 0x90, 60, 90, 0x90, 60, 0))
        assertEquals(listOf(true, false, true, false), p.map { it.down })
        assertEquals(1, p.map { it.code }.toSet().size)
        assertEquals(MidiCode.Kind.NOTE, MidiCode.kind(p[0].code))
    }

    @Test fun `program change só aperta`() {
        val p = MidiDecoder("").feed(bytes(0xC0, 3))
        assertEquals(1, p.size)
        assertTrue(p[0].down)
        assertEquals(MidiCode.Kind.PROGRAM, MidiCode.kind(p[0].code))
        assertEquals(3, MidiCode.number(p[0].code))
    }

    @Test fun `apertar de novo sem soltar é repetição e soltar sem apertar é ignorado`() {
        val d = MidiDecoder("")
        val p = d.feed(bytes(0xB0, 64, 0, 0xB0, 64, 127, 0xB0, 64, 120))
        assertEquals(listOf(0, 1), p.map { it.repeat })
    }

    @Test fun `sysex não vira botão`() {
        assertTrue(MidiDecoder("").feed(bytes(0xF0, 0x41, 0x10, 0x42, 0xF7)).isEmpty())
    }

    @Test fun `botão MIDI sem escolha não faz nada e com escolha faz o comando`() {
        val code = MidiCode.of(0xB0, 64)
        assertEquals(PedalAction.NONE, PedalAction.of(code))
        assertEquals(PedalAction.NEXT, PedalAction.of(code, PedalAction.assign(emptyMap(), code, PedalAction.NEXT)))
    }
}
