package br.app.stagebook

import br.app.stagebook.data.LibraryCodec
import br.app.stagebook.data.model.Library
import br.app.stagebook.data.model.Song
import br.app.stagebook.data.mergeSongs
import br.app.stagebook.domain.MidiBytes
import br.app.stagebook.domain.MidiKind
import br.app.stagebook.domain.MidiMessage
import br.app.stagebook.domain.SongMidi
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** MIDI on entering and leaving a song (user, 07/11): the standard bytes, kept with the song. */
class SongMidiTest {
    private fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test fun `program change, as gear numbers it`() =
        assertArrayEquals(b(0xC2, 4), MidiBytes.bytes(MidiMessage(MidiKind.PROGRAM, channel = 3, number = 5)))

    @Test fun `control change`() =
        assertArrayEquals(b(0xB0, 7, 100), MidiBytes.bytes(MidiMessage(MidiKind.CONTROL, channel = 1, number = 7, value = 100)))

    @Test fun `a note is pressed and released`() {
        val m = MidiMessage(MidiKind.NOTE, channel = 10, number = 36, value = 0)
        assertArrayEquals(b(0x99, 36, 1), MidiBytes.bytes(m))   // velocity 0 would be a release
        assertArrayEquals(b(0x89, 36, 0), MidiBytes.release(m))
        assertNull(MidiBytes.release(MidiMessage(MidiKind.PROGRAM)))
    }

    @Test fun `bank and program`() =
        assertArrayEquals(
            b(0xB1, 0, 1, 0xB1, 32, 2, 0xC1, 9),
            MidiBytes.bytes(MidiMessage(MidiKind.BANK_PROGRAM, channel = 2, number = 10, bank = 130)),
        )

    @Test fun `out of range values are clamped, never sent as written`() =
        assertArrayEquals(b(0xBF, 127, 0), MidiBytes.bytes(MidiMessage(MidiKind.CONTROL, channel = 40, number = 300, value = -5)))

    @Test fun `it goes in the library file and survives an import without MIDI`() {
        val midi = SongMidi(enter = listOf(MidiMessage(MidiKind.PROGRAM, number = 12)))
        val lib = Library(songs = listOf(Song(id = "a", title = "Flores", author = "Titãs", midi = midi)))
        assertEquals(midi, LibraryCodec.decode(LibraryCodec.encode(lib)) { "x" }.songs[0].midi)
        val (merged, _) = lib.mergeSongs(listOf(Song(title = "Flores", author = "Titãs", text = "nova")), { "y" })
        assertEquals(midi, merged.songs[0].midi)
    }

    @Test fun `on stage, the song before leaves before the next one enters`() {
        val sent = mutableListOf<Int>()
        val stage = br.app.stagebook.domain.StageMidi { list -> list.forEach { sent += it.number } }
        fun midi(enter: Int, exit: Int) = SongMidi(listOf(MidiMessage(number = enter)), listOf(MidiMessage(number = exit)))
        stage.show("a", midi(1, 2))
        stage.show("a", midi(1, 2))   // the same song again (a recomposition) sends nothing
        stage.show("b", midi(3, 4))
        stage.show("c", null)
        stage.leave()
        assertEquals(listOf(1, 2, 3, 4), sent)
    }
}
