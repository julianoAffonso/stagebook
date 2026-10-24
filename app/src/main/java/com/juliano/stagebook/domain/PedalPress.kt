package com.juliano.stagebook.domain

/**
 * Um aperto do pedal, venha ele como tecla (pedal Bluetooth comum) ou como mensagem MIDI (USB ou Bluetooth).
 * [code] é o keyCode do Android ou, para MIDI, o código de [MidiCode]; é a chave da escolha de comando nos ajustes.
 */
data class PedalPress(
    val code: Int,
    val down: Boolean,
    val repeat: Int = 0,
    val device: String = "",   // nome do aparelho externo que mandou; vazio = o próprio tablet
    val name: String = "",     // nome da tecla (só teclado; o do MIDI sai de MidiCode na tela)
)

/** Códigos dos botões MIDI, acima de qualquer keyCode do Android: tipo + canal + número (CC, nota ou programa). */
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

    /** Canal como o músico conta (1 a 16). */
    fun channel(code: Int): Int = ((code shr 8) and 0x0F) + 1

    fun number(code: Int): Int = code and 0x7F
}

/**
 * Lê o fluxo de bytes MIDI de um aparelho e devolve os apertos. Entende "running status" (mensagens seguidas sem
 * repetir o status, comum em BLE MIDI), ignora clock/realtime e SysEx. Botão de pedal:
 * - CC: valor ≥ 64 aperta, < 64 solta (pedais mandam 127 e 0);
 * - nota: Note On com velocidade aperta; Note Off ou velocidade 0 solta;
 * - Program Change: só aperta (não tem soltar).
 * Um segundo "aperta" sem soltar no meio vem como repetição, como o pé apoiado no teclado.
 */
class MidiDecoder(private val device: String) {
    private var status = 0
    private val data = IntArray(2)
    private var count = 0
    private var sysex = false
    private val held = HashSet<Int>()

    fun feed(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): List<PedalPress> {
        val out = ArrayList<PedalPress>(1)
        for (i in offset until offset + length) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b >= 0xF8 -> Unit                                   // realtime (clock etc.): pode vir no meio de tudo
                b == 0xF0 -> { sysex = true; status = 0 }
                b == 0xF7 -> sysex = false
                b >= 0xF0 -> { status = 0; sysex = false }          // comuns do sistema: sem botão
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
        // nota sai como Note On (0x90) dos dois jeitos: a mesma tecla aperta e solta
        val code = MidiCode.of(if (type == 0x80) 0x90 or (status and 0x0F) else status, data[0])
        if (type == 0xC0) return PedalPress(code, down = true, device = device)
        val repeat = if (down && !held.add(code)) 1 else 0
        if (!down && !held.remove(code)) return null   // soltar sem ter apertado (CC indo de 0 a 63): nada
        return PedalPress(code, down, repeat, device)
    }
}
