package com.juliano.stagebook

import com.juliano.stagebook.domain.LyricsView
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsViewTest {
    private val song = "{key: G}\n[Intro]\n[G] [D]\n\n[Verso 1]\n[G]Saio cedo pela [D]rua\n[Em]Onde a estrada vai [C]dar"

    @Test fun `a vista mostra só a letra`() {
        assertEquals("[Intro]\n\n[Verso 1]\nSaio cedo pela rua\nOnde a estrada vai dar", LyricsView.view(song))
    }

    @Test fun `seções e comentários do ChordPro continuam à vista`() {
        val cp = "{key: G}\n{start_of_chorus}\n[C]Oh oh\n{end_of_chorus}\n{c: Solo}\n[Am] [D]"
        assertEquals("{start_of_chorus}\nOh oh\n{end_of_chorus}\n{c: Solo}", LyricsView.view(cp))
        assertEquals(cp, LyricsView.merge(cp, LyricsView.view(cp)))
    }

    @Test fun `sem mudança a música volta igual`() {
        assertEquals(song, LyricsView.merge(song, LyricsView.view(song)))
    }

    @Test fun `palavra trocada mantém os acordes de antes e de depois dela`() {
        // o D estava no começo de "rua": o trecho antes dele não mudou, então fica no começo de "avenida"
        val edited = LyricsView.view(song).replace("pela rua", "pela avenida")
        assertEquals(
            "{key: G}\n[Intro]\n[G] [D]\n\n[Verso 1]\n[G]Saio cedo pela [D]avenida\n[Em]Onde a estrada vai [C]dar",
            LyricsView.merge(song, edited),
        )
        // o fim igual segura o acorde do fim
        val end = LyricsView.view(song).replace("Onde a estrada", "Para onde a estrada")
        // o Em do começo não tem trecho igual antes; o C segura pelo fim igual
        assertEquals(true, LyricsView.merge(song, end).contains("Para onde a estrada vai [C]dar"))
    }

    @Test fun `linha nova sem acorde e linha apagada sem levar o tom junto`() {
        val added = LyricsView.view(song) + "\nUma linha nova"
        assertEquals(song + "\nUma linha nova", LyricsView.merge(song, added))
        val removed = LyricsView.view(song).replace("\nOnde a estrada vai dar", "")
        assertEquals("{key: G}\n[Intro]\n[G] [D]\n\n[Verso 1]\n[G]Saio cedo pela [D]rua", LyricsView.merge(song, removed))
    }
}
