package br.app.stagebook.domain

import br.app.stagebook.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Most and least played (user, 19/11): once a show day, kept on the song, filtered and sorted for the Songs list. */
class PlayStatsTest {
    private val today = "2026-11-19"
    private fun song(title: String, plays: Int = 0, last: String = "") = Song(id = title, title = title, plays = plays, lastPlayed = last)

    @Test fun `a play counts once a show day`() {
        val once = PlayStats.record(song("A"), today)!!
        assertEquals(1, once.plays)
        assertEquals(today, once.lastPlayed)
        assertNull("the same day again changes nothing", PlayStats.record(once, today))
        assertEquals(2, PlayStats.record(once, "2026-11-20")!!.plays)
    }

    @Test fun `show all, the last 30 days or never played`() {
        val recent = song("R", 3, "2026-10-20")   // 30 days ago: still in
        val old = song("O", 5, "2026-10-19")      // 31 days ago: out
        val never = song("N")
        assertTrue(listOf(recent, old, never).all { PlayStats.shows(it, PlayShow.ALL, today) })
        assertTrue(PlayStats.shows(recent, PlayShow.RECENT, today))
        assertFalse(PlayStats.shows(old, PlayShow.RECENT, today))
        assertFalse(PlayStats.shows(never, PlayShow.RECENT, today))
        assertTrue(PlayStats.shows(never, PlayShow.NEVER, today))
        assertFalse(PlayStats.shows(recent, PlayShow.NEVER, today))
    }

    @Test fun `most played first, a tie to the one played last - least played starts with the never played`() {
        val list = listOf(song("A", 2, "2026-11-01"), song("B", 9, "2026-11-10"), song("C"), song("D", 2, "2026-11-15"))
        assertEquals(listOf("B", "D", "A", "C"), PlayStats.sorted(list, SongOrder.MOST) { it }.map { it.title })
        assertEquals(listOf("C", "A", "D", "B"), PlayStats.sorted(list, SongOrder.LEAST) { it }.map { it.title })
        assertEquals("A to Z keeps the title order", listOf("A", "B", "C", "D"), PlayStats.sorted(list, SongOrder.AZ) { it }.map { it.title })
    }

    @Test fun `a last play after today counts as today, in the filter as in the line`() {
        val ahead = song("F", 2, "2026-11-21")   // the clock moved back after it was played
        assertTrue(PlayStats.shows(ahead, PlayShow.RECENT, today))
        assertEquals(0L, PlayStats.daysAgo("2026-11-21", today))
    }

    @Test fun `how long ago, in today, yesterday, days, weeks and months`() {
        assertEquals(PlayAgo(PlayAgo.Unit.TODAY, 0), PlayStats.ago(today, today))
        assertEquals(PlayAgo(PlayAgo.Unit.YESTERDAY, 1), PlayStats.ago("2026-11-18", today))
        assertEquals(PlayAgo(PlayAgo.Unit.DAYS, 13), PlayStats.ago("2026-11-06", today))
        assertEquals(PlayAgo(PlayAgo.Unit.WEEKS, 2), PlayStats.ago("2026-11-05", today))
        assertEquals(PlayAgo(PlayAgo.Unit.MONTHS, 2), PlayStats.ago("2026-09-19", today))
        assertNull(PlayStats.ago("", today))
        assertNull(PlayStats.ago("not a date", today))
    }

    // user, 19/11: "Never played" with "Most played" was a choice that meant nothing
    @Test fun `never played sorts only A to Z, the other shows take every order`() {
        assertEquals(listOf(SongOrder.AZ), PlayStats.ordersFor(PlayShow.NEVER))
        assertEquals(SongOrder.entries, PlayStats.ordersFor(PlayShow.RECENT))
        assertEquals(SongOrder.AZ, PlayStats.orderFor(PlayShow.NEVER, SongOrder.MOST))
        assertEquals(SongOrder.LEAST, PlayStats.orderFor(PlayShow.RECENT, SongOrder.LEAST))
    }
}
