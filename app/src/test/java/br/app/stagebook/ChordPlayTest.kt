package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import br.app.stagebook.data.model.ChordPlayMode
import br.app.stagebook.data.model.Instrument
import br.app.stagebook.domain.ChordSynth
import br.app.stagebook.domain.Voicings
import br.app.stagebook.ui.stage.ChordShapePopup
import br.app.stagebook.ui.theme.StagebookTheme
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

    @Test fun `notes of open G on acoustic guitar - capo 2 raises them a whole step`() {
        val g = Voicings.Shape(listOf(3, 2, 0, 0, 0, 3))
        assertEquals(listOf(43, 47, 50, 55, 59, 67), Voicings.midi(g, Instrument.ACOUSTIC_GUITAR))
        assertEquals(listOf(45, 49, 52, 57, 61, 69), Voicings.midi(g, Instrument.ACOUSTIC_GUITAR, capo = 2))
        assertEquals(listOf(47, 50, 55, 59, 67), Voicings.midi(Voicings.Shape(listOf(-1, 2, 0, 0, 0, 3)), Instrument.ACOUSTIC_GUITAR))   // corda x não toca
    }

    @Test fun `picked takes longer than strummed - both is the longest - no clipping`() {
        val notes = listOf(43, 47, 50, 55, 59, 67)
        val arp = ChordSynth.render(notes, ChordPlayMode.ARPEGGIO, keyboard = false)
        val chord = ChordSynth.render(notes, ChordPlayMode.CHORD, keyboard = false)
        val both = ChordSynth.render(notes, ChordPlayMode.BOTH, keyboard = true)
        assertTrue(arp.size > chord.size)
        assertTrue(both.size > arp.size)
        assertTrue(chord.any { it.toInt() != 0 })
        assertTrue(both.maxOf { kotlin.math.abs(it.toInt()) } < Short.MAX_VALUE)
    }

    @Test fun `chord window - play over the diagram sends the notes and with capo explains the diagram`() {
        var played: List<Int>? = null
        compose.setContent {
            StagebookTheme { ChordShapePopup("G", Instrument.ACOUSTIC_GUITAR, "{capo: 2}\n[G]linha", {}, {}, onPlay = { played = it }) }
        }
        compose.onNodeWithText("O desenho é de G. Com capo na 2ª casa, soa A.").assertExists()
        compose.onNodeWithContentDescription("Tocar").performClick()
        assertTrue(played!!.isNotEmpty())
        assertTrue(played!!.all { it >= 40 + 2 })   // tudo pelo menos 2 casas acima da corda mais grave
    }

    @Test fun `keyboard does not use capo - no notice and no higher note`() {
        var played: List<Int>? = null
        compose.setContent {
            StagebookTheme { ChordShapePopup("C", Instrument.KEYBOARD, "{capo: 2}\n[C]linha", {}, {}, onPlay = { played = it }) }
        }
        compose.onNodeWithText("O desenho é de", substring = true).assertDoesNotExist()   // teclado: sem a nota do desenho com capo
        compose.onNodeWithContentDescription("Tocar").performClick()
        assertEquals(Voicings.keys("C").first(), played)
    }

    @Test fun `Details - degree in the song's key, the chord's field and where else it shows up - and back`() {
        compose.setContent { StagebookTheme { ChordShapePopup("C", Instrument.ACOUSTIC_GUITAR, "{key: G}\n[G]um [C]dois", {}, {}) } }
        compose.onNodeWithText("Detalhes").performClick()
        compose.onNodeWithText("Detalhes de C").assertExists()
        compose.onNodeWithText("No tom da música (G)").assertExists()
        compose.onNodeWithText("C é o IV neste tom.").assertExists()
        compose.onNodeWithText("Campo harmônico de C").assertExists()
        compose.onNodeWithText("V em F").assertExists()
        compose.onNodeWithContentDescription("Voltar ao desenho").performClick()
        compose.onNodeWithContentDescription("Tocar").assertExists()
    }
}
