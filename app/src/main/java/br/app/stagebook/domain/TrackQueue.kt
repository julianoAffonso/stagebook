package br.app.stagebook.domain

/**
 * Which backing track the player holds while songs change on stage. Live, the track playing goes on until it's stopped
 * (a slip of the finger back to the previous song stopped it mid-song; device test, 02/11): the song on screen only
 * takes over when the track stops, by the button or at its end.
 */
class TrackQueue<T : Any> {
    /** The track the player holds (null = the song on screen has none). */
    var wanted: T? = null
        private set
    private var pending: T? = null
    private var hasPending = false

    /** The song on screen changed to one with [track]. True when the player should load it now. */
    @Synchronized fun open(track: T?, playing: Boolean): Boolean {
        if (playing) {
            pending = track
            hasPending = track != wanted
            return false
        }
        hasPending = false
        if (track == wanted) return false
        wanted = track
        return true
    }

    /** The track stopped (button or end): the song on screen's track, if it changed meanwhile. True when it should load. */
    @Synchronized fun stopped(): Boolean {
        if (!hasPending) return false
        hasPending = false
        wanted = pending
        return true
    }
}
