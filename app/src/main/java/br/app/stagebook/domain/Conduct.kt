package br.app.stagebook.domain

import br.app.stagebook.data.model.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * "Lead the band": a band device sends the setlist and the current song; followers open the song by themselves, without
 * needing the setlist, only the song. Before starting, the leader sends [CHECK] and each one answers what's missing;
 * everyone's missing songs come back in every message ([Msg.missing]) so the whole band sees the alert, otherwise the
 * song starts and someone has no lyrics. Travels encrypted as [BandProtocol.Kind.CONDUCT].
 *
 * The leader's break (a setlist's break on its stage) goes in the same [OPEN] message, as [Msg.breakMs]: an app that
 * doesn't know the field reads the message as before and stays on the song before the break.
 */
object Conduct {
    const val CHECK = "check"   // this is the setlist: what are you missing?
    const val OPEN = "open"     // open song [Msg.index]
    const val END = "end"       // it's over: each one is on their own again

    @Serializable
    data class Item(val title: String, val artist: String = "")

    /** What a band member is missing ([who] = device name). */
    @Serializable
    data class Lack(val who: String, val titles: List<String>)

    @Serializable
    data class Msg(
        val type: String,
        val from: String = "",
        val set: String = "",
        val songs: List<Item> = emptyList(),
        val index: Int = -1,
        val missing: List<Lack> = emptyList(),
        // the leader is on the break after song [index], for this long when the message left (-1 = no break). How long,
        // not since when: two devices' clocks never agree. Left out of the message when there is no break
        val breakMs: Long = -1,
    ) {
        /**
         * How long the leader's break has lasted, or null when there's none. Only what makes sense counts: a break
         * comes after the song on stage and before another one of the setlist, and doesn't last days.
         */
        val rest: Long? get() = breakMs.takeIf { type == OPEN && it in 0..MAX_BREAK_MS && index >= 0 && index + 1 < songs.size }
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(msg: Msg): String = json.encodeToString(Msg.serializer(), msg)

    fun decode(body: String): Msg? = runCatching { json.decodeFromString(Msg.serializer(), body) }.getOrNull()
        ?.takeIf { it.type in setOf(CHECK, OPEN, END) && it.songs.size <= MAX_SONGS }

    /**
     * The song in the follower's library. Only "found" counts: similar becomes missing, so the alert errs on the safe
     * side.
     */
    fun find(item: Item, library: List<Song>): Song? =
        (SetlistMatcher.match(QrPayload.Entry(item.title, item.artist), library) as? QrMatch.Found)?.song

    fun missing(songs: List<Item>, library: List<Song>): List<String> = songs.filter { find(it, library) == null }.map { it.title }

    /** The missing songs that apply to song [index] (for the alert when opening). */
    fun missingAt(msg: Msg, index: Int): List<String> {
        val title = msg.songs.getOrNull(index)?.title ?: return emptyList()
        return msg.missing.filter { title in it.titles }.map { it.who }
    }

    /** What the follower answers: the setlist songs not here (empty list = has them all). */
    fun lackFor(me: String, msg: Msg, library: List<Song>): Lack = Lack(me, missing(msg.songs, library))

    /** The missing songs worth an alert (whoever has everything is left out), ordered by name. */
    fun lacks(replies: Collection<Lack>): List<Lack> = replies.filter { it.titles.isNotEmpty() }.sortedBy { it.who }

    const val MAX_SONGS = 200
    const val MAX_BREAK_MS = 24 * 60 * 60_000L
}
