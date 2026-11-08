package br.app.stagebook.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Settings "test the metronome" button: a click at [BPM] with no count-in, started and stopped by the person. Nothing
 * runs until it's pressed. [start] sounds it through the same path as the stage; a change of volume or output is heard
 * at the next start (simpler than restarting a click that is already playing).
 */
class MetronomeTest(private val start: (bpm: Int) -> Unit, private val stop: () -> Unit) {
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    fun toggle() = if (_playing.value) stop() else begin()

    private fun begin() {
        _playing.value = true
        start(BPM)
    }

    /** Safe to call anytime: leaving the screen stops it, and nothing happens when it isn't playing. */
    fun stop() {
        if (!_playing.value) return
        _playing.value = false
        stop.invoke()
    }

    companion object { const val BPM = 100 }
}
