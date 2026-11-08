package br.app.stagebook.domain

/**
 * When the open USB stream is idle and may be closed. The stream moves about a thousand packets a second even with
 * nothing to say, and most songs have no backing track, so after [IDLE_MS] of nothing sounding the player closes it
 * and opens it again on the next play or click (user, 08/11). Pure rules: the service owns the timer and the stream.
 */
object StreamSleep {
    const val IDLE_MS = 10_000L

    /**
     * The direct USB stream is open and nothing uses it: no backing track playing, no click in the engine, no play
     * waiting to start, and not already closed or opening. Only this path: through Android and on the device's own
     * output the system owns the stream.
     */
    @JvmStatic
    fun idle(
        running: Boolean, streamOpen: Boolean, playing: Boolean, clickOn: Boolean,
        playWanted: Boolean, connecting: Boolean, asleep: Boolean,
    ): Boolean = running && streamOpen && !playing && !clickOn && !playWanted && !connecting && !asleep

    /**
     * The interface is gone: it was there, nothing is open and nothing is opening. A stream closed on purpose is not
     * a lost interface (no "disconnected" notice, no shutdown countdown for it).
     */
    @JvmStatic
    fun interfaceLost(hadUsb: Boolean, linkOpen: Boolean, androidOpen: Boolean, connecting: Boolean, asleep: Boolean): Boolean =
        hadUsb && !linkOpen && !androidOpen && !connecting && !asleep
}
