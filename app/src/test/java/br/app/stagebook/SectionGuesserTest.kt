package br.app.stagebook

import br.app.stagebook.data.model.SectionLang
import br.app.stagebook.domain.SectionGuesser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Made-up verses: only the structure matters.
class SectionGuesserTest {
    private val v1 = "primeira estrofe linha um\nprimeira estrofe linha dois"
    private val v2 = "segunda estrofe linha um\nsegunda estrofe linha dois"
    private val v3 = "terceira estrofe linha um\nterceira estrofe linha dois"
    private val pre = "chegando no refrão\nsobe a tensão"
    private val ch = "esse é o refrão\nque todo mundo canta\ne repete no show"
    private val br = "a ponte muda tudo\nantes do último refrão"

    private fun song(vararg parts: String) = parts.joinToString("\n\n")
    private fun labels(text: String?) = text!!.lines().filter { it.startsWith("[") }

    @Test
    fun `verse chorus verse chorus`() {
        assertEquals(listOf("[Verso 1]", "[Refrão]", "[Verso 2]", "[Refrão]"), labels(SectionGuesser.guess(song(v1, ch, v2, ch), SectionLang.PT)))
    }

    @Test
    fun `pre-chorus before each chorus and bridge at the end`() {
        val text = song(v1, pre, ch, v2, pre, ch, br, ch)
        assertEquals(
            listOf("[Verso 1]", "[Pré-refrão]", "[Refrão]", "[Verso 2]", "[Pré-refrão]", "[Refrão]", "[Ponte]", "[Refrão]"),
            labels(SectionGuesser.guess(text, SectionLang.PT)),
        )
    }

    @Test
    fun `chorus with one different line is still the chorus, in English`() {
        val chVariant = "esse é o refrão\nque todo mundo canta\ne repete mais uma vez"
        assertEquals(
            listOf("[Verse 1]", "[Chorus]", "[Verse 2]", "[Chorus]", "[Verse 3]"),
            labels(SectionGuesser.guess(song(v1, ch, v2, chVariant, v3), SectionLang.EN)),
        )
    }

    @Test
    fun `verse that comes back the same keeps its number`() {
        assertEquals(listOf("[Verso 1]", "[Refrão]", "[Verso 2]", "[Refrão]", "[Verso 1]"), labels(SectionGuesser.guess(song(v1, ch, v2, ch, v1), SectionLang.PT)))
    }

    @Test
    fun `no repeats or already titled is left alone`() {
        assertNull(SectionGuesser.guess(song(v1, v2, v3), SectionLang.PT))
        assertNull(SectionGuesser.guess("[Refrão]\n" + song(ch, v1, ch), SectionLang.PT))
        assertNull(SectionGuesser.guess(v1, SectionLang.PT))
    }

    @Test
    fun `the lyrics words do not change, only titles come in`() {
        val text = song(v1, ch, v2, ch)
        val guessed = SectionGuesser.guess("$text\n\n\n", SectionLang.PT)!!
        assertEquals(text, guessed.lines().filterNot { it.startsWith("[") }.joinToString("\n"))
    }
}
