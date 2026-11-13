package br.app.stagebook.domain

/**
 * Following the band: what the leader is on (a song, or a break) opens the follower's stage, but never takes them off a
 * screen where something is being typed or chosen. There it waits, only the latest one, and opens when they come back
 * to a screen that can be left, if the leader is still on it.
 */
class PendingFollow {
    var waiting: String? = null
        private set

    /** The leader moved to [target]. Returns it when the screen on now can be left for the stage ([pulls]); else it waits. */
    fun arrive(target: String, pulls: Boolean): String? {
        waiting = if (pulls) null else target
        return target.takeIf { pulls }
    }

    /** Now on a screen that [pulls] or not: what waited, when it's still what the leader is on ([now]). */
    fun release(pulls: Boolean, now: String?): String? {
        val target = waiting ?: return null
        if (!pulls) return null
        waiting = null
        return target.takeIf { it == now }
    }
}
