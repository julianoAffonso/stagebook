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

    /**
     * What a track's measures are kept under (user, 22/11: another audio file means measuring again): its name, size, when
     * it was written and a checksum of its start ([head]). A file put in place of another, even with the same name, size
     * and date, gets a new key.
     */
    fun cacheKey(name: String, bytes: Long, modified: Long, head: Long): String = "$name|$bytes|$modified|$head"

    /** The checksum of the first [HEAD_BYTES] of a file, for [cacheKey]. */
    fun headOf(input: java.io.InputStream): Long {
        val crc = java.util.zip.CRC32()
        val buf = ByteArray(HEAD_BYTES)
        var got = 0
        while (got < HEAD_BYTES) { val n = input.read(buf, got, HEAD_BYTES - got); if (n < 0) break; got += n }
        crc.update(buf, 0, got)
        return crc.value
    }

    const val HEAD_BYTES = 64 * 1024

    /**
     * The kept keys to drop when writing [current] for the file [name]: those of files no longer in the folder
     * ([present]) and the older ones of the same name. A key with more after it ("|mask") still belongs to [current].
     */
    fun stale(keys: Collection<String>, present: Set<String>, name: String, current: String): List<String> = keys.filter { k ->
        val file = k.substringBefore('|')
        file !in present || (file == name && k != current && !k.startsWith("$current|"))
    }
}
