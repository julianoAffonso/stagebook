package br.app.stagebook.domain

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The MIDI messages a song can send (user, 07/11): the standard ones a stage rig listens to. */
@Serializable
enum class MidiKind {
    @SerialName("program") PROGRAM,             // a preset: program 1–128
    @SerialName("control") CONTROL,             // controller 0–127 to a value 0–127
    @SerialName("note") NOTE,                   // note 0–127 at a velocity 1–127, pressed and released
    @SerialName("bank_program") BANK_PROGRAM,   // bank 0–16383 (CC0/CC32) and then the program
}

/**
 * One MIDI message as the person writes it: channel 1–16, program 1–128 (as gear shows them), the rest 0–127. Out of
 * range values are clamped when sending, never sent as written.
 */
@Immutable
@Serializable
data class MidiMessage(
    val kind: MidiKind = MidiKind.PROGRAM,
    val channel: Int = 1,
    val number: Int = 1,   // program, controller or note
    val value: Int = 0,    // controller value or note velocity
    val bank: Int = 0,
)

/** What a song sends when it shows on stage and when the stage leaves it. Saved with the song (backup, editor). */
@Immutable
@Serializable
data class SongMidi(val enter: List<MidiMessage> = emptyList(), val exit: List<MidiMessage> = emptyList()) {
    fun isEmpty() = enter.isEmpty() && exit.isEmpty()
}

/** The bytes on the wire. */
object MidiBytes {
    const val CHANNELS = 16
    const val PROGRAMS = 128
    const val MAX_DATA = 127
    const val MAX_BANK = 16383
    const val NOTE_MS = 100L   // how long a note stays pressed before its release

    private fun status(kind: Int, channel: Int) = (kind or (channel.coerceIn(1, CHANNELS) - 1)).toByte()
    private fun data(v: Int) = v.coerceIn(0, MAX_DATA).toByte()

    /** What goes out at once; a note's release comes after [NOTE_MS] ([release]). */
    fun bytes(m: MidiMessage): ByteArray = when (m.kind) {
        MidiKind.PROGRAM -> byteArrayOf(status(0xC0, m.channel), data(m.number.coerceIn(1, PROGRAMS) - 1))
        MidiKind.CONTROL -> byteArrayOf(status(0xB0, m.channel), data(m.number), data(m.value))
        MidiKind.NOTE -> byteArrayOf(status(0x90, m.channel), data(m.number), data(m.value.coerceIn(1, MAX_DATA)))
        MidiKind.BANK_PROGRAM -> {
            val bank = m.bank.coerceIn(0, MAX_BANK)
            byteArrayOf(
                status(0xB0, m.channel), 0, data(bank shr 7),
                status(0xB0, m.channel), 32, data(bank and MAX_DATA),
                status(0xC0, m.channel), data(m.number.coerceIn(1, PROGRAMS) - 1),
            )
        }
    }

    /** The messages with every value inside MIDI's ranges (what came from outside the app's own fields). */
    fun held(midi: SongMidi): SongMidi {
        fun h(m: MidiMessage) = m.copy(
            channel = m.channel.coerceIn(1, CHANNELS),
            number = if (m.kind == MidiKind.PROGRAM || m.kind == MidiKind.BANK_PROGRAM) m.number.coerceIn(1, PROGRAMS) else m.number.coerceIn(0, MAX_DATA),
            value = if (m.kind == MidiKind.NOTE) m.value.coerceIn(1, MAX_DATA) else m.value.coerceIn(0, MAX_DATA),
            bank = m.bank.coerceIn(0, MAX_BANK),
        )
        return SongMidi(midi.enter.map(::h), midi.exit.map(::h))
    }

    /** A note's release (note off); nothing for the others. */
    fun release(m: MidiMessage): ByteArray? =
        if (m.kind == MidiKind.NOTE) byteArrayOf(status(0x80, m.channel), data(m.number), 0) else null
}

/**
 * The song on stage and what it sends when it leaves. Kept outside the stage: following the leader, the next song's
 * stage comes in before the old one leaves, and the old song's exit has to go before the new song's entry.
 */
class StageMidi(private val send: (List<MidiMessage>) -> Unit) {
    private var song: String? = null
    private var exit: List<MidiMessage> = emptyList()

    fun show(id: String?, midi: SongMidi?) {
        // the same song again (a recomposition, or the stage reopened after the messages were edited): the entry isn't
        // sent twice, but the exit is read again: it was kept from before the edit (found on a phone, 07/11)
        if (id == song) { exit = midi?.exit.orEmpty(); return }
        send(exit)
        send(midi?.enter.orEmpty())
        song = id
        exit = midi?.exit.orEmpty()
    }

    fun leave() {
        send(exit)
        song = null
        exit = emptyList()
    }
}
