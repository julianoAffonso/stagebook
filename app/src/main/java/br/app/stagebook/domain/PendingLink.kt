package br.app.stagebook.domain

/**
 * A receive link that arrives while the stage is open waits (the show is never interrupted) and opens when the person
 * leaves the stage. Only the latest waiting code is kept. A code already open on the receive screen is never reopened:
 * that would discard a review in progress.
 */
class PendingLink {
    enum class Arrival { OPEN, HELD, IGNORED }

    var pending: String? = null
        private set

    /** A link came in. [openCode]: the code on the receive screen now, if that's where the person is. */
    fun arrive(code: String, onStage: Boolean, openCode: String?): Arrival = when {
        code == openCode -> { if (pending == code) pending = null; Arrival.IGNORED }
        onStage -> if (pending == code) Arrival.IGNORED else { pending = code; Arrival.HELD }
        else -> { pending = null; Arrival.OPEN }
    }

    /** The stage was left: the waiting code to open now, or null. */
    fun release(openCode: String?): String? {
        val code = pending ?: return null
        pending = null
        return code.takeIf { it != openCode }
    }
}
