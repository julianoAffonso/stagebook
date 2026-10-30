package br.app.stagebook

import br.app.stagebook.domain.LyricsCleaner
import org.junit.Assert.assertEquals
import org.junit.Test

// Lyrics made up for the test (no copyrighted content).
class LyricsCleanerTest {
    private val english = listOf(
        "I walked along the river in the night",
        "You told me that the stars were not so bright",
        "And I know that you are never coming back",
        "But I will wait for you down by the track",
    )
    private val portuguese = listOf(
        "Eu andei pela beira do rio de noite",
        "Você me disse que as estrelas não brilhavam",
        "E eu sei que você nunca mais vai voltar",
        "Mas eu vou te esperar lá perto dos trilhos",
    )

    @Test
    fun `removes chords-only lines`() {
        val raw = "Am   F   C   G\nEu andei pela beira do rio\nD/F#  Em7\nVocê me disse"
        assertEquals("Eu andei pela beira do rio\nVocê me disse", LyricsCleaner.clean(raw))
    }

    @Test
    fun `line by line translation with Portuguese first keeps only the English`() {
        // the way letras.mus.br's traducao.html page shows it: translation on top, original below
        val mixed = english.indices.flatMap { listOf(portuguese[it], english[it]) }.joinToString("\n")
        assertEquals(english.joinToString("\n"), LyricsCleaner.clean(mixed))
    }

    @Test
    fun `block translation keeps only the English`() {
        val blocks = english.joinToString("\n") + "\n\n" + portuguese.joinToString("\n")
        assertEquals(english.joinToString("\n"), LyricsCleaner.clean(blocks))
    }

    @Test
    fun `song in Portuguese does not change`() {
        val pt = portuguese.joinToString("\n") + "\n\n" + portuguese.reversed().joinToString("\n")
        assertEquals(pt, LyricsCleaner.clean(pt))
    }

    @Test
    fun `song in English with one Portuguese word does not change`() {
        val en = (english + "Saudade, my old friend") .joinToString("\n")
        assertEquals(en, LyricsCleaner.clean(en))
    }

    @Test
    fun `repeated chorus with no recognizable language does not duplicate`() {
        val mixed = listOf(portuguese[0], english[0], "Oh, oh, oh", "Oh, oh, oh", portuguese[1], english[1], portuguese[2], english[2], portuguese[3], english[3])
        assertEquals((listOf(english[0], "Oh, oh, oh") + english.drop(1)).joinToString("\n"), LyricsCleaner.clean(mixed.joinToString("\n")))
    }
}
