package br.app.stagebook.domain

/**
 * The USB output dropped while a song played: the stage keeps waiting for it, and a cable that comes back within
 * [GIVE_UP_MS] carries on by itself where it was. Past that the wait ends (user, 09/11: the button stayed on Stop for
 * good with the interface gone): the button shows Play again and the position is kept for the next play.
 */
object OutputWait {
    // the same 10 s as the idle stream (StreamSleep): one rule for "the interface is away", in sync if either changes
    const val GIVE_UP_MS = StreamSleep.IDLE_MS

    /** Still waiting after [waitedMs] without the output? Never once it's [connected] again. */
    fun keepWaiting(waitedMs: Long, connected: Boolean): Boolean = !connected && waitedMs < GIVE_UP_MS

    /** The wait ended with the output still away: stop waiting, keep the position. */
    fun givesUp(waitedMs: Long, connected: Boolean): Boolean = !connected && waitedMs >= GIVE_UP_MS
}
