package br.app.stagebook.domain

/**
 * How a backing track starts and stops (user, 22/11): it can start at its first sound, skipping the silence before it,
 * and the volume can go down over a while when the person stops. The engine does the sound; these are the numbers.
 */
object TrackEnds {
    /** Over this (linear, about -60 dBFS) a sample is sound. */
    const val SOUND = 0.001f
    /** The search for the first sound stops here: a longer silence is the song's own. */
    const val MAX_SILENCE_MS = 30_000L
    /** A little of the silence stays, so the first note's attack isn't cut. */
    const val LEAD_PAD_MS = 50L
    /** After the fade the engine has played its last bits; the jump to the start waits this much more. */
    const val FADE_SLACK_MS = 60L
    /** The fade lengths offered in Settings; 0 = only the anti-click. */
    val FADES_MS = listOf(0, 500, 1000, 2000, 3000, 5000)

    /** Where the track starts, from where its first sound is ([firstSoundMs], -1 when it couldn't be read). */
    fun startAt(firstSoundMs: Long): Long = (firstSoundMs - LEAD_PAD_MS).coerceAtLeast(0)

    /** How long the jump back to the start waits after a stop, so it doesn't come in the middle of the fade. */
    fun seekDelay(fadeMs: Int): Long = if (fadeMs > 0) fadeMs + FADE_SLACK_MS else 0
}
