package br.app.stagebook.domain

/**
 * Which backing track the player holds while songs change on stage. Live, the track playing goes on until it's stopped
 * (a slip of the finger back to the previous song stopped it mid-song; device test, 02/11): the song on screen only
 * takes over when the track stops, by the button, at its end, or when it fails to load. The queue keeps its own
 * "playing" so a song change and the track's end can't interleave between reading it and queueing (audit 02/11).
 */
class TrackQueue<T : Any> {
    /** The track the player holds (null = the song on screen has none). */
    @Volatile var wanted: T? = null
        private set
    private var pending: T? = null
    private var hasPending = false
    private var playing = false

    /** The song on screen changed to one with [track]. True when the player should load it now. */
    @Synchronized fun open(track: T?): Boolean {
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

    /** Play was pressed: from now on, song changes wait for the track to stop. */
    @Synchronized fun started() { playing = true }

    /** The track stopped (button, end, or a failed load): the song on screen's track, if it changed. True to load it. */
    @Synchronized fun stopped(): Boolean {
        playing = false
        if (!hasPending) return false
        hasPending = false
        wanted = pending
        return true
    }
}
