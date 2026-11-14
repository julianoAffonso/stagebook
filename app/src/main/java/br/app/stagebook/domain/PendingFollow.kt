package br.app.stagebook.domain

/** Why a follower isn't taken to the leader's song yet: what the person is in the middle of. */
enum class FollowWait {
    /** A screen where something is typed or chosen (editor, importer, a setlist being changed...). */
    SCREEN,

    /** A menu or window is open. */
    MENU,

    /** The keyboard is open. */
    KEYBOARD,
}

/**
 * Whether the screen the follower is on lets the leader's song take them to the stage: null = yes, else the reason it
 * waits. The stage follows (it replaces itself) unless a menu or window of its own is open. [pulls]: a screen that can be left; [setlistBusy]: that
 * screen is busy; [menu]: a menu or window is open; [keyboard]: the keyboard is open.
 */
fun followBlock(pulls: Boolean, setlistBusy: Boolean, onStage: Boolean, menu: Boolean, keyboard: Boolean): FollowWait? = when {
    !pulls || setlistBusy -> FollowWait.SCREEN
    menu -> FollowWait.MENU
    onStage -> null
    keyboard -> FollowWait.KEYBOARD
    else -> null
}

/**
 * Following the band: what the leader is on (a song, or a break) opens the follower's stage, but never takes them off a
 * screen where something is being typed or chosen, or out of the middle of an action (a menu, a window, the keyboard).
 * There it waits, only the latest one, for as long as it takes, and opens when they come back to a screen that can be
 * left, if the leader is still on it.
 */
class PendingFollow {
    var waiting: String? = null
        private set

    /** Why it waits (null when nothing does). */
    var reason: FollowWait? = null
        private set

    /** The leader moved to [target]. Returns it when the person can be taken now ([block] null); else it waits. */
    fun arrive(target: String, block: FollowWait?): String? {
        waiting = if (block == null) null else target
        reason = block
        return target.takeIf { block == null }
    }

    fun arrive(target: String, pulls: Boolean): String? = arrive(target, if (pulls) null else FollowWait.SCREEN)

    /** Now blocked by [block] or not (null): what waited, when it's still what the leader is on ([now]). */
    fun release(block: FollowWait?, now: String?): String? {
        val target = waiting ?: return null
        if (block != null) { reason = block; return null }
        return take()?.takeIf { it == now }
    }

    fun release(pulls: Boolean, now: String?): String? = release(if (pulls) null else FollowWait.SCREEN, now)

    /** What waited, taken for opening now ("Go"); it stops waiting. */
    fun take(): String? = waiting.also { waiting = null; reason = null }

    fun clear() { waiting = null; reason = null }
}
