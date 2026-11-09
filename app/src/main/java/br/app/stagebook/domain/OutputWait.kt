package br.app.stagebook.domain

/**
 * The USB output dropped while a song played: the stage keeps waiting for it, and a cable that comes back within
 * [GIVE_UP_MS] carries on by itself where it was. Past that the wait ends (user, 09/11: the button stayed on Stop for
 * good with the interface gone): the button shows Play again and the position is kept for the next play.
 */
object OutputWait {
    // the same 10 s as the idle stream (StreamSleep): one rule for "the interface is away", in sync if either changes
    const val GIVE_UP_MS = StreamSleep.IDLE_MS
    // plugged in but the connection never done (a permission still asked, an interface that doesn't answer): not for ever
    const val PLUGGED_MAX_MS = 3 * GIVE_UP_MS

    /**
     * Still waiting after [waitedMs] without the output? Never once it's [connected] again. The time counts until the
     * cable is back, not until the connection is done: with the interface [plugged] in, Android and the reconnect may
     * take a few more seconds, and the song still carries on by itself (user, 09/11: plugged back in time, it gave up
     * 1 s before the connection was done).
     */
    fun keepWaiting(waitedMs: Long, connected: Boolean, plugged: Boolean = false): Boolean =
        !connected && (waitedMs < GIVE_UP_MS || (plugged && waitedMs < PLUGGED_MAX_MS))

    /** The wait ended with the output still away and unplugged: stop waiting, keep the position. */
    fun givesUp(waitedMs: Long, connected: Boolean, plugged: Boolean = false): Boolean =
        !connected && waitedMs >= GIVE_UP_MS && (!plugged || waitedMs >= PLUGGED_MAX_MS)
}
