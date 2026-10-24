package com.juliano.stagebook

import com.juliano.stagebook.data.model.SectionLang
import com.juliano.stagebook.domain.Sections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SectionsTest {
    @Test
    fun `reconhece títulos em português e inglês em vários formatos`() {
        val headers = listOf(
            "[Chorus]", "Refrão:", "REFRÃO", "(Refrão 2x)", "Verso 2", "[Verse 1]", "Estrofe", "Ponte", "Bridge:",
            "Intro", "Solo", "[Solo de guitarra]", "Final", "Fim", "Outro", "Pré-refrão", "[Pre-Chorus]",
            "Interlúdio", "Instrumental", "Primeira parte", "Segunda Parte:", "Refrão x2", "Refrão (3 vezes)",
            "**Chorus**", "# Verse", "Coda",
        )
        headers.forEach { assertNotNull(it, Sections.detect(it)) }
    }

    @Test
    fun `não confunde letra com título`() {
        listOf(
            "Refrão de amor que eu canto pra você", "The chorus of the birds", "Fim de tarde na praia",
            "Solo eu e você", "Intro to the night we had", "",
        ).forEach { assertNull(it, Sections.detect(it)) }
    }

    @Test
    fun `nome no idioma escolhido com número e repetição`() {
        val h = Sections.detect("[Chorus 2] (x2)")!!
        assertEquals("Refrão 2  2x", Sections.label(h, SectionLang.PT))
        assertEquals("Chorus 2  2x", Sections.label(h, SectionLang.EN))
        assertEquals("Fim", Sections.label(Sections.detect("Outro")!!, SectionLang.PT))
        assertEquals("Verso 2", Sections.label(Sections.detect("Segunda parte")!!, SectionLang.PT))
    }

    @Test
    fun `padronizar troca só os títulos`() {
        val text = "Chorus:\nletra um\n\nVerse 2\nletra dois"
        assertEquals("[Refrão]\nletra um\n\n[Verso 2]\nletra dois", Sections.normalize(text, SectionLang.PT))
    }
}
