package br.app.stagebook.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Word splitting used to tell the language of a line, and the shared cleanup patterns. */
class TextPatternsTest {
    private fun words(s: String) = TextPatterns.words(s).toList()

    @Test fun `words are lowercase letters split on anything else`() {
        assertEquals(listOf("eu", "sei", "que", "vou", "te", "amar"), words("Eu sei, que vou... TE amar!"))
        assertEquals(listOf("ação", "coração"), words("Ação 123 coração"))
    }

    @Test fun `the typographic apostrophe counts as the plain one`() {
        assertEquals(listOf("don't", "i'm"), words("Don’t I’m"))
        assertEquals(listOf("don't"), words("don't"))
    }

    @Test fun `quotes around a word don't count`() {
        assertEquals(listOf("hello", "world"), words("'hello' ’world’"))
        assertTrue(words("'' ' ").isEmpty())
    }

    @Test fun `the typographic apostrophe makes an english line count as english`() {
        assertEquals(LyricsCleaner.Lang.EN, LyricsCleaner.langOf("I don’t know what you’re doing to me"))
    }

    @Test fun `trailing spaces and three or more blank lines are found`() {
        assertEquals("abc", "abc  \t".replace(TextPatterns.TRAILING_SPACE, ""))
        assertEquals("a\n\nb", "a\n\n\n\n\nb".replace(TextPatterns.MANY_BLANK, "\n\n"))
        assertEquals("a\n\nb", "a\n\nb".replace(TextPatterns.MANY_BLANK, "\n\n"))
    }
}
