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
    val value: Int = -1,       // CC value (0 to 127); -1 for keys, notes and program changes
)

/**
 * Whether "Set up a button" looks at this press: a press, or any CC with its value. The Hotone's second button sends
 * only CC 0, which reads as a release, and was never recorded (device test, 01/11); [PedalLearn] drops the 0 that is
 * just the foot coming off another button.
 */
val PedalPress.learnable: Boolean get() = down || value >= 0

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

    // a CC button told apart by its value: pedals like the Hotone send the same CC on two buttons, one with 127 and
    // the other with 0 (user, 31/10)
    private const val VALUED = 0x100000

    fun withValue(code: Int, value: Int): Int = code or VALUED or ((value and 0x7F) shl 25)

    /** The CC value this button was set by, or null for a button that's only the CC (or not a CC). */
    fun value(code: Int): Int? = if (isMidi(code) && code and VALUED != 0) (code ushr 25) and 0x7F else null
}

/**
 * Which code "Set up a button" records. A CC is recorded with its value, so two buttons on the same CC (127 and 0)
 * become two buttons; the 0 a regular pedal sends when the foot comes off right after its 127 isn't a button.
 */
class PedalLearn {
    private val pressedAt = HashMap<Int, Long>()   // CC → when it last came with a value ≥ 64

    fun code(p: PedalPress, now: Long): Int? {
        if (p.value < 0) return p.code
        if (p.value >= 64) pressedAt[p.code] = now
        else if (pressedAt[p.code]?.let { now - it < RELEASE_MS } == true) return null
        return MidiCode.withValue(p.code, p.value)
    }

    private companion object {
        const val RELEASE_MS = 1_500L
    }
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
 * Every CC comes out with its value, a low one without a press before it too: it can be a button of its own
 * ([PedalAction.hit]).
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
        if (type == 0xB0) {
            if (!down) held.remove(code)
            return PedalPress(code, down, repeat, device, value = value)
        }
        if (!down && held.remove(code) == null) return null   // releasing a note that wasn't pressed: nothing
        return PedalPress(code, down, repeat, device)
    }
}
