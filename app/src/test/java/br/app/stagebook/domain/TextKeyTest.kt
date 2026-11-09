package br.app.stagebook.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** The title key shared between tablets and the one A to Z order of the app. */
class TextKeyTest {
    @Test fun `accents and case don't change the key`() {
        assertEquals(TextKey.of("Canção"), TextKey.of("CANCAO"))
        assertEquals("ave maria", TextKey.of("Áve Maríã"))
    }

    @Test fun `apostrophes are dropped, so its and it's are the same title`() {
        assertEquals("its my life", TextKey.of("It's My Life"))
        assertEquals("its my life", TextKey.of("It’s my life"))   // typographic apostrophe of most sites
        assertEquals("dont stop", TextKey.of("Don´t Stop"))
        assertEquals("dont stop", TextKey.of("Don`t stop"))
    }

    @Test fun `punctuation and repeated spaces become one space, trimmed`() {
        assertEquals("rock n roll 2", TextKey.of("  Rock-n-Roll (2)!  "))
        assertEquals("a b", TextKey.of("a   ...   b"))
        assertEquals("", TextKey.of("?!"))
    }

    @Test fun `null and empty give an empty key`() {
        assertEquals("", TextKey.of(null))
        assertEquals("", TextKey.of(""))
    }

    @Test fun `letters outside a to z don't survive (the key is ascii)`() {
        assertEquals("", TextKey.of("日本"))
        assertEquals("abc 1", TextKey.of("abc·1"))
    }

    @Test fun `sorted goes a to z ignoring case, accents and leading spaces`() {
        val names = listOf("zebra", "  Água", "beta", "Alpha", "ébano")
        assertEquals(listOf("  Água", "Alpha", "beta", "ébano", "zebra"), TextKey.sorted(names) { it })
    }

    @Test fun `sorted keeps the original order of equal keys`() {
        val items = listOf("B" to 1, "a" to 2, "A" to 3, "á" to 4)
        assertEquals(listOf(2, 3, 4, 1), TextKey.sorted(items) { it.first }.map { it.second })
    }
}
