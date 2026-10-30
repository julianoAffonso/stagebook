package br.app.stagebook.domain

import android.view.KeyEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The pedal works like a keyboard: each key runs a stage command. Common pedal keys come preset; in Settings → Pedal
 * each button can be set to another command (like another app).
 */
@Serializable
enum class PedalAction {
    @SerialName("next") NEXT,
    @SerialName("previous") PREVIOUS,
    @SerialName("nextSong") NEXT_SONG,
    @SerialName("previousSong") PREVIOUS_SONG,
    @SerialName("playPause") PLAY_PAUSE,   // play/pause the backing track (only in player mode and with a song that has a backing track)
    @SerialName("none") NONE;

    companion object {
        /** The key's command: the one chosen in settings or, with no choice, the default. */
        fun of(keyCode: Int, custom: Map<Int, PedalAction> = emptyMap()): PedalAction = custom[keyCode] ?: default(keyCode)

        fun default(keyCode: Int): PedalAction = when (keyCode) {
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_N -> NEXT

            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_DEL -> PREVIOUS

            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> PLAY_PAUSE

            else -> NONE
        }

        /** Saves a button's choice; resetting to default removes the key from the list. */
        fun assign(custom: Map<Int, PedalAction>, keyCode: Int, action: PedalAction): Map<Int, PedalAction> =
            if (action == default(keyCode)) custom - keyCode else custom + (keyCode to action)
    }
}
