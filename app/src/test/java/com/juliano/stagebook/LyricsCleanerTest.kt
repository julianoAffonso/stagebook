package com.juliano.stagebook

import com.juliano.stagebook.domain.LyricsCleaner
import org.junit.Assert.assertEquals
import org.junit.Test

// Letras inventadas para o teste (sem conteúdo com direito autoral).
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
    fun `tira linhas só de acordes`() {
        val raw = "Am   F   C   G\nEu andei pela beira do rio\nD/F#  Em7\nVocê me disse"
        assertEquals("Eu andei pela beira do rio\nVocê me disse", LyricsCleaner.clean(raw))
    }

    @Test
    fun `tradução linha a linha com o português primeiro fica só o inglês`() {
        // como a página traducao.html do letras.mus.br mostra: tradução em cima, original embaixo
        val mixed = english.indices.flatMap { listOf(portuguese[it], english[it]) }.joinToString("\n")
        assertEquals(english.joinToString("\n"), LyricsCleaner.clean(mixed))
    }

    @Test
    fun `tradução em blocos fica só o inglês`() {
        val blocks = english.joinToString("\n") + "\n\n" + portuguese.joinToString("\n")
        assertEquals(english.joinToString("\n"), LyricsCleaner.clean(blocks))
    }

    @Test
    fun `música em português não muda`() {
        val pt = portuguese.joinToString("\n") + "\n\n" + portuguese.reversed().joinToString("\n")
        assertEquals(pt, LyricsCleaner.clean(pt))
    }

    @Test
    fun `música em inglês com uma palavra em português não muda`() {
        val en = (english + "Saudade, my old friend") .joinToString("\n")
        assertEquals(en, LyricsCleaner.clean(en))
    }

    @Test
    fun `refrão repetido sem língua reconhecível não duplica`() {
        val mixed = listOf(portuguese[0], english[0], "Oh, oh, oh", "Oh, oh, oh", portuguese[1], english[1], portuguese[2], english[2], portuguese[3], english[3])
        assertEquals((listOf(english[0], "Oh, oh, oh") + english.drop(1)).joinToString("\n"), LyricsCleaner.clean(mixed.joinToString("\n")))
    }
}
