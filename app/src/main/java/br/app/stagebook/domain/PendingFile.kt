package br.app.stagebook.domain

/**
 * A file tapped outside the app ([T]: where the checked copy is). Opened right away, except with the stage open: then
 * it waits (the show is never interrupted) and opens when the person leaves the stage. Only the latest file waits.
 * A file that isn't Stagebook's ([arrive] with null) opens nothing.
 */
class PendingFile<T : Any> {
    enum class Arrival { OPEN, HELD, REJECTED }

    var pending: T? = null
        private set

    /** A file came in: [file] is null when it was rejected. */
    fun arrive(file: T?, onStage: Boolean): Arrival = when {
        file == null -> Arrival.REJECTED
        onStage -> { pending = file; Arrival.HELD }
        else -> { pending = null; Arrival.OPEN }
    }

    /** The stage was left: the waiting file to open now, or null. */
    fun release(): T? = pending.also { pending = null }
}
