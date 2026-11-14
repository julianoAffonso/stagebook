package br.app.stagebook.domain

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Where a show is in its setlist: the songs already played, the one on stage now and the next. The marks belong to
 * this device and to one day: they are never part of the setlist that is shared, and a show on another day starts
 * clean.
 */
object SetlistShow {
    /** A song counts as played after this long on stage in a row (a song only passed by isn't one). */
    const val PLAYED_AFTER_MS = 60_000L

    /** One setlist's marks: the [day] of the last one, the songs [played] and the [current] one ("" = none). */
    @Immutable
    @Serializable
    data class Marks(val day: String = "", val played: List<String> = emptyList(), val current: String = "") {
        val empty: Boolean get() = played.isEmpty() && current.isEmpty()
    }

    enum class State { NONE, PLAYED, NOW, NEXT }

    /** The marks when the stage opens on [day]: the ones from another day are gone. */
    fun opened(marks: Marks, day: String): Marks = if (marks.day == day) marks else Marks(day)

    /** [songId] is on stage. */
    fun onStage(marks: Marks, day: String, songId: String): Marks = marks.copy(day = day, current = songId)

    /** [songId] stayed on stage long enough. */
    fun played(marks: Marks, day: String, songId: String): Marks =
        if (songId in marks.played) marks.copy(day = day) else marks.copy(day = day, played = marks.played + songId)

    /** Only the marks of songs still in the setlist ([items]: its song ids). */
    fun pruned(marks: Marks, items: Collection<String>): Marks {
        val played = marks.played.filter { it in items }
        val current = marks.current.takeIf { it in items }.orEmpty()
        return if (played.size == marks.played.size && current == marks.current) marks else marks.copy(played = played, current = current)
    }

    /**
     * What each song of the setlist shows. The one on stage wins over played, and so does the one after it (going back
     * to a song makes it "now" again). A break between two songs isn't a song, so "next" simply is the following one.
     */
    fun states(items: List<String>, marks: Marks?): List<State> {
        if (marks == null || marks.empty) return List(items.size) { State.NONE }
        val now = if (marks.current.isEmpty()) -1 else items.indexOf(marks.current)
        return items.mapIndexed { i, id ->
            when {
                i == now -> State.NOW
                now >= 0 && i == now + 1 -> State.NEXT
                id in marks.played -> State.PLAYED
                else -> State.NONE
            }
        }
    }
}
