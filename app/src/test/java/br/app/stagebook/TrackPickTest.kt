package br.app.stagebook

import br.app.stagebook.domain.TrackPick
import br.app.stagebook.domain.TrackPick.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The songs that stayed without data on the user's tablet (22/11), with what Deezer answered for them. */
class TrackPickTest {
    @Test fun `the studio version under the version Deezer adds to the title`() {
        val beatles = listOf(
            Result(1, "A Hard Day's Night (Remastered 2015)", "The Beatles"),
            Result(2, "A Hard Day's Night (2023 Mix)", "The Beatles"),
            Result(3, "A Hard Day's Night (Live / Remastered)", "The Beatles"),
        )
        assertEquals(1L, TrackPick.best(beatles, "A Hard Day’s Night", "The Beatles"))
        val lua = listOf(
            Result(1, "A Tal Canção Pra Lua (Spect3r & Apollo 55 Remix)", "Vitor Kley"),
            Result(2, "A Tal Canção Pra Lua (Microfonado)", "Vitor Kley"),
            Result(3, "A Tal Canção Pra Lua (Ao Vivo)", "Vitor Kley"),
        )
        // a duo where Deezer has one of them; the remix and the live one lose to the plain recording
        assertEquals(2L, TrackPick.best(lua, "A Tal Canção Pra Lua", "Vitor Kley e Samuel Rosa"))
    }

    @Test fun `a live song only found live, and a live song in the library prefers the live recording`() {
        val skank = listOf(Result(1, "Acima do Sol Ao Vivo", "Skank"), Result(2, "Acima do Sol (Skank cover)", "IAlterando"))
        assertEquals(1L, TrackPick.best(skank, "Acima do Sol", "Skank"))
        val both = listOf(Result(1, "Alma Matters", "Morrissey"), Result(2, "Alma Matters (Live)", "Morrissey"))
        assertEquals(2L, TrackPick.best(both, "Alma Matters (Ao Vivo)", "Morrissey"))
    }

    @Test fun `a small typo in the artist still finds the song, another artist doesn't`() {
        val alma = listOf(Result(1, "Alma Matters", "Morrissey"), Result(3, "Alma Matters", "Sweet Little Band"))
        assertEquals(1L, TrackPick.best(alma, "Alma Matters", "Morrisey"))
        assertTrue(TrackPick.sameArtist("Tracy Chapman", "Trace Chapman"))
        assertTrue(TrackPick.sameArtist("Red Hot Chili Peppers", "Red Hot Chilli Peppers"))
        assertTrue(TrackPick.sameArtist("Crowded House", "Crownded House"))
        assertTrue(TrackPick.sameArtist("Iggy Pop", "Iggy Pop e  Kate Pierson"))
        assertFalse(TrackPick.sameArtist("Skank", "Spank"))     // short names: no typo allowance
        assertFalse(TrackPick.sameArtist("Sweet Little Band", "Morrisey"))
        assertNull(TrackPick.best(listOf(Result(1, "Alma Matters", "Sweet Little Band")), "Alma Matters", "Morrisey"))
    }

    @Test fun `another song with a similar title is never taken`() {
        assertNull(TrackPick.best(listOf(Result(1, "All My Loving You", "The Beatles")), "All My Loving", "The Beatles"))
        assertEquals("All My Loving", TrackPick.baseTitle("All My Loving (Remastered 2009)"))
        assertEquals("Acima do Sol", TrackPick.baseTitle("Acima do Sol Ao Vivo"))
        assertEquals("Wonderwall", TrackPick.baseTitle("Wonderwall - Remastered"))
    }
}
