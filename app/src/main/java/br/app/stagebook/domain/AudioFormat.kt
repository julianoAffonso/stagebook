package br.app.stagebook.domain

/**
 * What an audio file really is, by its first bytes (user's request of 30/10: a backing track is checked inside, not
 * only by its name). "ogg", "mp3", "wav" or null: anything else (a photo or a document renamed .mp3) doesn't come in.
 */
object AudioFormat {
    const val HEADER = 12

    fun of(head: ByteArray): String? {
        fun text(from: Int, s: String) = head.size >= from + s.length && s.indices.all { head[from + it] == s[it].code.toByte() }
        return when {
            text(0, "OggS") -> "ogg"
            text(0, "RIFF") && text(8, "WAVE") -> "wav"
            text(0, "ID3") -> "mp3"
            head.size >= 2 && head[0] == 0xFF.toByte() && (head[1].toInt() and 0xE0) == 0xE0 -> "mp3"   // MPEG frame sync
            else -> null
        }
    }
}
