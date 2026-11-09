package br.app.stagebook.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Small rules behind "same song" and the tag filter: title similarity, artist keys and tags still in use. */
class MatchingRulesTest {
    @Test fun `similarity is 1 for equal titles and 0 against an empty one`() {
        assertEquals(1.0, SetlistMatcher.similarity("hazard", "hazard"), 0.0)
        assertEquals(1.0, SetlistMatcher.similarity("", ""), 0.0)
        assertEquals(0.0, SetlistMatcher.similarity("hazard", ""), 0.0)
        assertEquals(0.0, SetlistMatcher.similarity("", "hazard"), 0.0)
    }

    @Test fun `similarity drops by one edit over the longer title`() {
        assertEquals(1 - 1.0 / 6, SetlistMatcher.similarity("hazard", "hazrd"), 1e-9)   // missing letter
        assertEquals(1 - 1.0 / 6, SetlistMatcher.similarity("hazard", "hazxrd"), 1e-9)   // wrong letter
        assertEquals(1 - 2.0 / 6, SetlistMatcher.similarity("hazard", "hzaard"), 1e-9)   // two letters swapped
        assertEquals(0.0, SetlistMatcher.similarity("abc", "xyz"), 1e-9)
    }

    @Test fun `similarity is symmetric`() {
        listOf("imagine" to "imagin", "yesterday" to "yesterdays", "a" to "abc").forEach { (a, b) ->
            assertEquals(SetlistMatcher.similarity(a, b), SetlistMatcher.similarity(b, a), 1e-9)
        }
    }

    @Test fun `an unknown artist matches any, and a shorter name inside the full one matches`() {
        assertTrue(SongMatch.sameArtistKey("", "bon jovi"))
        assertTrue(SongMatch.sameArtistKey("bon jovi", ""))
        assertTrue(SongMatch.sameArtistKey("bon jovi", "jon bon jovi"))
        assertFalse(SongMatch.sameArtistKey("bon jovi", "talk talk"))
    }

    @Test fun `artists are compared without case or accents`() {
        assertTrue(SetlistMatcher.sameArtist("Djavan", "DJAVAN"))
        assertTrue(SetlistMatcher.sameArtist("Caetano Veloso", "caetano"))
        assertTrue(SetlistMatcher.sameArtist("Legião Urbana", "legiao urbana"))
        assertFalse(SetlistMatcher.sameArtist("Bon Jovi", "Talk Talk"))
    }

    @Test fun `a chosen tag no song has any more leaves the filter, ignoring case`() {
        val all = listOf("Rock", "MPB")
        assertEquals(setOf("rock", "MPB"), Tags.stillUsed(setOf("rock", "MPB", "Samba"), all))
        assertEquals(emptySet<String>(), Tags.stillUsed(setOf("Samba"), all))
        assertEquals(emptySet<String>(), Tags.stillUsed(setOf("Rock"), emptyList()))
    }
}
