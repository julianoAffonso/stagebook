package br.app.stagebook.data.web

import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.SetlistCloud
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.Chords
import br.app.stagebook.domain.QrMatch
import br.app.stagebook.domain.QrPayload
import br.app.stagebook.domain.SetlistMatcher
import br.app.stagebook.domain.SongMatch
import br.app.stagebook.domain.TextKey
import kotlinx.serialization.Serializable

/**
 * Import → Type the 3 words, from the computer (T37f4): the tablet fetches what was sent over the internet and the page
 * reviews it with the app's rules. A song I have stays mine, a song that came along goes into the library, a similar
 * one is the user's choice (or left out). Creating also lets the sender into the band, as accepting in the app does.
 */
class WebReceive(
    private val repository: LibraryRepository,
    private val receive: suspend (String) -> SetlistCloud.Received,
    private val accepted: (code: String, SetlistCloud.Received.Ok) -> Unit = { _, _ -> },
    private val inboxDone: (String) -> Unit = {},
) {
    @Serializable
    data class Candidate(val id: String, val title: String, val author: String)

    /** One row: "have" (mine), "came" (goes in as new), "similar" (pick one or leave out), "missing" (left out). */
    @Serializable
    data class Row(val title: String, val artist: String, val status: String, val candidates: List<Candidate> = emptyList())

    @Serializable
    data class Review(val token: String, val name: String, val songsOnly: Boolean, val rows: List<Row>)

    private class Pending(
        val code: String, val received: SetlistCloud.Received.Ok, val name: String, val found: List<String?>,
        val came: Map<Int, SetlistCloud.SharedSong>, val inbox: String,
    )

    private val pending = mutableMapOf<String, Pending>()

    /** Result of fetching: the review, or why not ("not_found", "offline", "newer"). */
    sealed interface Fetched {
        data class Ok(val review: Review) : Fetched
        data class Problem(val error: String) : Fetched
    }

    suspend fun fetch(code: String): Fetched = when (val r = receive(code)) {
        is SetlistCloud.Received.Ok -> review(code, r)?.let { Fetched.Ok(it) } ?: Fetched.Problem("not_found")
        SetlistCloud.Received.NotFound -> Fetched.Problem("not_found")
        SetlistCloud.Received.Offline -> Fetched.Problem("offline")
        is SetlistCloud.Received.Newer -> Fetched.Problem("newer")
        // an invite to the band is accepted on the device, not in the editor on the computer
        is SetlistCloud.Received.Invite -> Fetched.Problem("not_found")
    }

    /** Received from the band (Import → Received from the band): the same review; once done it leaves the inbox. */
    fun fromInbox(id: String, text: String, songs: List<SetlistCloud.SharedSong>): Review? = review("", SetlistCloud.Received.Ok(text, songs), id)

    @Synchronized
    private fun review(code: String, r: SetlistCloud.Received.Ok, inbox: String = ""): Review? {
        val parsed = QrPayload.parse(r.text) ?: return null
        val library = repository.library.value.songs
        val songsOnly = parsed.name.isEmpty()
        fun came(e: QrPayload.Entry) = r.songs.firstOrNull { s ->
            TextKey.of(s.title) == TextKey.of(e.title) && SongMatch.sameArtistKey(TextKey.of(s.author), TextKey.of(e.artist))
        }
        val came = mutableMapOf<Int, SetlistCloud.SharedSong>()
        val found = mutableListOf<String?>()
        val rows = parsed.songs.mapIndexed { i, e ->
            val m = SetlistMatcher.match(e, library)
            val c = if (m is QrMatch.Missing || (songsOnly && m is QrMatch.Similar)) came(e) else null
            if (c != null) came[i] = c
            found += (m as? QrMatch.Found)?.song?.id
            when {
                m is QrMatch.Found -> Row(e.title, e.artist, "have")
                c != null -> Row(e.title, e.artist, "came")
                m is QrMatch.Similar -> Row(e.title, e.artist, "similar", m.candidates.map { Candidate(it.id, it.title, it.author) })
                else -> Row(e.title, e.artist, "missing")
            }
        }
        val token = java.util.UUID.randomUUID().toString()
        pending.clear()   // one review at a time is enough for one computer
        pending[token] = Pending(code, r, parsed.name, found, came, inbox)
        return Review(token, parsed.name, songsOnly, rows)
    }

    /**
     * Creates the setlist (named [name], "Show (2)" if taken) with the songs I have, the ones that came and the
     * [choices] for similar rows (row index → song id); loose songs only add the ones that came. Returns the setlist id
     * ("" for loose songs), or null if the review expired.
     */
    @Synchronized
    fun create(token: String, name: String, choices: Map<Int, String>): String? {
        val p = pending.remove(token) ?: return null
        val slots = p.found.mapIndexed { i, id ->
            id ?: choices[i]?.takeIf { repository.song(it) != null } ?: p.came[i]?.let { s ->
                repository.saveSong(Song(title = s.title, author = s.author, year = s.year, text = Chords.withDetectedKey(s.text)))
            }
        }
        if (p.code.isNotEmpty()) accepted(p.code, p.received)
        if (p.inbox.isNotEmpty()) inboxDone(p.inbox)
        if (p.name.isEmpty()) return ""
        val setId = repository.createSetlist(uniqueName(name.trim().ifEmpty { p.name }))
        repository.setSetlistItems(setId, slots.filterNotNull())
        return setId
    }

    private fun uniqueName(name: String): String {
        fun taken(n: String) = repository.library.value.sets.any { TextKey.of(it.name) == TextKey.of(n) }
        if (!taken(name)) return name
        var n = 2
        while (taken("$name ($n)")) n++
        return "$name ($n)"
    }
}
