package com.juliano.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.juliano.stagebook.data.model.ChordPlayMode
import com.juliano.stagebook.data.model.Instrument
import com.juliano.stagebook.domain.ChordSynth
import com.juliano.stagebook.domain.Voicings
import com.juliano.stagebook.ui.stage.ChordShapePopup
import com.juliano.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tocar o acorde (T20): as notas do desenho (com capo, a nota real), o som em cada modo e o botão na janela do acorde. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ChordPlayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `notas do G aberto no violão - com capo 2 sobem um tom`() {
        val g = Voicings.Shape(listOf(3, 2, 0, 0, 0, 3))
        assertEquals(listOf(43, 47, 50, 55, 59, 67), Voicings.midi(g, Instrument.VIOLAO))
        assertEquals(listOf(45, 49, 52, 57, 61, 69), Voicings.midi(g, Instrument.VIOLAO, capo = 2))
        assertEquals(listOf(47, 50, 55, 59, 67), Voicings.midi(Voicings.Shape(listOf(-1, 2, 0, 0, 0, 3)), Instrument.VIOLAO))   // corda x não toca
    }

    @Test fun `dedilhado demora mais que junto - os dois é o mais longo - sem estourar`() {
        val notes = listOf(43, 47, 50, 55, 59, 67)
        val arp = ChordSynth.render(notes, ChordPlayMode.ARPEGGIO, keyboard = false)
        val chord = ChordSynth.render(notes, ChordPlayMode.CHORD, keyboard = false)
        val both = ChordSynth.render(notes, ChordPlayMode.BOTH, keyboard = true)
        assertTrue(arp.size > chord.size)
        assertTrue(both.size > arp.size)
        assertTrue(chord.any { it.toInt() != 0 })
        assertTrue(both.maxOf { kotlin.math.abs(it.toInt()) } < Short.MAX_VALUE)
    }

    @Test fun `janela do acorde - o play sobre o desenho manda as notas e com capo explica o desenho`() {
        var played: List<Int>? = null
        compose.setContent {
            StagebookTheme { ChordShapePopup("G", Instrument.VIOLAO, "{capo: 2}\n[G]linha", {}, {}, onPlay = { played = it }) }
        }
        compose.onNodeWithText("O desenho é o de G; com capo na 2ª casa, soa A.").assertExists()
        compose.onNodeWithContentDescription("Tocar").performClick()
        assertTrue(played!!.isNotEmpty())
        assertTrue(played!!.all { it >= 40 + 2 })   // tudo pelo menos 2 casas acima da corda mais grave
    }

    @Test fun `teclado não usa capo - nem aviso nem nota mais alta`() {
        var played: List<Int>? = null
        compose.setContent {
            StagebookTheme { ChordShapePopup("C", Instrument.TECLADO, "{capo: 2}\n[C]linha", {}, {}, onPlay = { played = it }) }
        }
        compose.onNodeWithText("com capo", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Tocar").performClick()
        assertEquals(Voicings.keys("C").first(), played)
    }
}
