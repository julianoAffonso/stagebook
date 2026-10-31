package br.app.stagebook.domain

/**
 * A pedal press, whether it comes as a key (regular Bluetooth pedal) or a MIDI message (USB or Bluetooth). [code] is
 * Android's keyCode or, for MIDI, the [MidiCode] code; it's the key for the command choice in settings.
 */
data class PedalPress(
    val code: Int,
    val down: Boolean,
    val repeat: Int = 0,
    val device: String = "",   // name of the external device that sent it; empty = the tablet itself
    val name: String = "",     // key name (keyboard only; the MIDI one comes from MidiCode on screen)
)

/** MIDI button codes, above any Android keyCode: type + channel + number (CC, note or program). */
object MidiCode {
    private const val BASE = 0x1000000

    enum class Kind { CC, NOTE, PROGRAM }

    fun of(status: Int, number: Int): Int = BASE or ((status and 0xF0) shl 12) or ((status and 0x0F) shl 8) or (number and 0x7F)

    fun isMidi(code: Int): Boolean = code and BASE != 0

    fun kind(code: Int): Kind = when ((code shr 12) and 0xF0) {
        0xB0 -> Kind.CC
        0xC0 -> Kind.PROGRAM
        else -> Kind.NOTE
    }

    /** Channel the way musicians count (1 to 16). */
    fun channel(code: Int): Int = ((code shr 8) and 0x0F) + 1

    fun number(code: Int): Int = code and 0x7F
}

/**
 * Reads a device's MIDI byte stream and returns the presses. Understands "running status" (consecutive messages without
 * repeating the status, common in BLE MIDI), ignores clock/realtime and SysEx. Pedal button:
 * - CC: value ≥ 64 presses, < 64 releases (pedals send 127 and 0);
 * - note: Note On with velocity presses; Note Off or velocity 0 releases;
 * - Program Change: only presses (it has no release).
 * Many pedals send only the press (CC 127 or Note On every time, never the release): the same message again while
 * "held" is a new press (user, 31/10: the pedal moved the song once and then never again). A CC going up through
 * other values without a release (expression pedal, knob) comes as a repeat, so it doesn't fire on every step.
 */
class MidiDecoder(private val device: String) {
    private var status = 0
    private val data = IntArray(2)
    private var count = 0
    private var sysex = false
    private val held = HashMap<Int, Int>()   // code → value of the press (CC value or velocity)

    fun feed(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): List<PedalPress> {
        val out = ArrayList<PedalPress>(1)
        for (i in offset until offset + length) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b >= 0xF8 -> Unit                                   // realtime (clock etc.): can come in the middle of anything
                b == 0xF0 -> { sysex = true; status = 0 }
                b == 0xF7 -> sysex = false
                b >= 0xF0 -> { status = 0; sysex = false }          // system common: no button
                b >= 0x80 -> { status = b; count = 0; sysex = false }
                sysex || status == 0 -> Unit
                else -> {
                    data[count++] = b
                    if (count == needed(status)) {
                        count = 0
                        press()?.let(out::add)
                    }
                }
            }
        }
        return out
    }

    private fun needed(s: Int) = if (s and 0xF0 == 0xC0 || s and 0xF0 == 0xD0) 1 else 2

    private fun press(): PedalPress? {
        val type = status and 0xF0
        val down = when (type) {
            0xB0 -> data[1] >= 64
            0x90 -> data[1] > 0
            0x80 -> false
            0xC0 -> true
            else -> return null
        }
        // a note comes out as Note On (0x90) both ways: the same key presses and releases
        val code = MidiCode.of(if (type == 0x80) 0x90 or (status and 0x0F) else status, data[0])
        if (type == 0xC0) return PedalPress(code, down = true, device = device)
        val value = data[1]
        val repeat = if (down) {
            val before = held.put(code, value)
            if (before == null || before == value || type == 0x90) 0 else 1
        } else 0
        if (!down && held.remove(code) == null) return null   // releasing without having pressed (CC going from 0 to 63): nothing
        return PedalPress(code, down, repeat, device)
    }
}
