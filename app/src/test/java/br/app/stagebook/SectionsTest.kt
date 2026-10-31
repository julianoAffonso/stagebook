package br.app.stagebook

import br.app.stagebook.data.model.SectionLang
import br.app.stagebook.domain.Sections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SectionsTest {
    @Test
    fun `recognizes titles in Portuguese and English in several formats`() {
        val headers = listOf(
            "[Chorus]", "Refrão:", "REFRÃO", "(Refrão 2x)", "Verso 2", "[Verse 1]", "Estrofe", "Ponte", "Bridge:",
            "Intro", "Solo", "[Solo de guitarra]", "Final", "Fim", "Outro", "Pré-refrão", "[Pre-Chorus]",
            "Interlúdio", "Instrumental", "Primeira parte", "Segunda Parte:", "Refrão x2", "Refrão (3 vezes)",
            "**Chorus**", "# Verse", "Coda",
        )
        headers.forEach { assertNotNull(it, Sections.detect(it)) }
    }

    @Test
    fun `does not mistake lyrics for a title`() {
        listOf(
            "Refrão de amor que eu canto pra você", "The chorus of the birds", "Fim de tarde na praia",
            "Solo eu e você", "Intro to the night we had", "",
        ).forEach { assertNull(it, Sections.detect(it)) }
    }

    @Test
    fun `name in the chosen language with number and repeat`() {
        val h = Sections.detect("[Chorus 2] (x2)")!!
        assertEquals("Refrão 2  2x", Sections.label(h, SectionLang.PT))
        assertEquals("Chorus 2  2x", Sections.label(h, SectionLang.EN))
        assertEquals("Fim", Sections.label(Sections.detect("Outro")!!, SectionLang.PT))
        assertEquals("Verso 2", Sections.label(Sections.detect("Segunda parte")!!, SectionLang.PT))
    }

    @Test
    fun `standardizing changes only the titles`() {
        val text = "Chorus:\nletra um\n\nVerse 2\nletra dois"
        assertEquals("[Refrão]\nletra um\n\n[Verso 2]\nletra dois", Sections.normalize(text, SectionLang.PT))
    }

    // Cifra Club writes the tab's part as "[Tab - Intro]": the section is the Intro, the tab word is only the source
    @org.junit.Test fun `a tab title keeps its section`() {
        assertEquals(Sections.Kind.INTRO, Sections.detect("[Tab - Intro]")?.kind)
        assertEquals(Sections.Kind.SOLO, Sections.detect("[Tab: Solo]")?.kind)
        assertEquals(Sections.Kind.CHORUS, Sections.detect("[Tab Refrão]")?.kind)
        assertEquals(null, Sections.detect("[Tab - Intro] and some lyrics here"))
    }
}
