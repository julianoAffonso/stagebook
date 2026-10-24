package com.juliano.stagebook.domain

import android.view.KeyEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * O pedal funciona como teclado: cada tecla faz um comando no palco. As teclas comuns de pedal já vêm
 * ligadas; em Ajustes → Pedal cada botão pode ser ligado a outro comando (como no another app).
 */
@Serializable
enum class PedalAction {
    @SerialName("next") NEXT,
    @SerialName("previous") PREVIOUS,
    @SerialName("nextSong") NEXT_SONG,
    @SerialName("previousSong") PREVIOUS_SONG,
    @SerialName("none") NONE;

    companion object {
        /** O comando da tecla: o escolhido nos ajustes ou, sem escolha, o padrão. */
        fun of(keyCode: Int, custom: Map<Int, PedalAction> = emptyMap()): PedalAction = custom[keyCode] ?: default(keyCode)

        fun default(keyCode: Int): PedalAction = when (keyCode) {
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_N -> NEXT

            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_DEL -> PREVIOUS

            else -> NONE
        }

        /** Grava a escolha de um botão; voltar ao padrão tira a tecla da lista. */
        fun assign(custom: Map<Int, PedalAction>, keyCode: Int, action: PedalAction): Map<Int, PedalAction> =
            if (action == default(keyCode)) custom - keyCode else custom + (keyCode to action)
    }
}
