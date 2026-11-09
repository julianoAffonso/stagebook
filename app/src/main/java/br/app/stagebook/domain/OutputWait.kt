package br.app.stagebook.domain

/**
 * The USB output dropped while a song played: the stage keeps waiting for it (the track button blinks the USB
 * unplugged icon), and a cable that comes back within [GIVE_UP_MS] carries on by itself where it was. Past that the song
 * stops and goes back to the start, so the Play that shows again means what it always means (user, 09/11: a Play that
 * kept the position looked like it would start over).
 */
object OutputWait {
    const val GIVE_UP_MS = 60_000L   // a cable that fell mid-show is usually back well within a minute (user, 09/11)
    // plugged in but the connection never done (a permission still asked, an interface that doesn't answer): not for ever
    const val PLUGGED_MAX_MS = GIVE_UP_MS + 20_000L

    /**
     * Still waiting after [waitedMs] without the output? Never once it's [connected] again. The time counts until the
     * cable is back, not until the connection is done: with the interface [plugged] in, Android and the reconnect may
     * take a few more seconds, and the song still carries on by itself (user, 09/11: plugged back in time, it gave up
     * 1 s before the connection was done).
     */
    fun keepWaiting(waitedMs: Long, connected: Boolean, plugged: Boolean = false): Boolean =
        !connected && (waitedMs < GIVE_UP_MS || (plugged && waitedMs < PLUGGED_MAX_MS))

    /** The wait ended with the output still away: the song stops and goes back to the start. */
    fun givesUp(waitedMs: Long, connected: Boolean, plugged: Boolean = false): Boolean =
        !connected && waitedMs >= GIVE_UP_MS && (!plugged || waitedMs >= PLUGGED_MAX_MS)
}
