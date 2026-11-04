package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
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
import org.robolectric.annotation.GraphicsMode

/**
 * Playing the chord (T20): the diagram's notes (with a capo, the real note), the sound in each mode and the button in
 * the chord window.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ChordPlayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `notes of open G on acoustic guitar - capo 2 raises them a whole step`() {
        val g = Voicings.Shape(listOf(3, 2, 0, 0, 0, 3))
        assertEquals(listOf(43, 47, 50, 55, 59, 67), Voicings.midi(g, Instrument.ACOUSTIC_GUITAR))
        assertEquals(listOf(45, 49, 52, 57, 61, 69), Voicings.midi(g, Instrument.ACOUSTIC_GUITAR, capo = 2))
        assertEquals(listOf(47, 50, 55, 59, 67), Voicings.midi(Voicings.Shape(listOf(-1, 2, 0, 0, 0, 3)), Instrument.ACOUSTIC_GUITAR))   // string x is not played
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
        compose.onNodeWithText("O diagrama é de G. Com capo na 2ª casa, soa A.").assertExists()
        compose.onNodeWithContentDescription("Tocar").performClick()
        assertTrue(played!!.isNotEmpty())
        assertTrue(played!!.all { it >= 40 + 2 })   // everything at least 2 frets above the lowest string
    }

    @Test fun `keyboard does not use capo - no notice and no higher note`() {
        var played: List<Int>? = null
        compose.setContent {
            StagebookTheme { ChordShapePopup("C", Instrument.KEYBOARD, "{capo: 2}\n[C]linha", {}, {}, onPlay = { played = it }) }
        }
        compose.onNodeWithText("O diagrama é de", substring = true).assertDoesNotExist()   // keyboard: no diagram note with capo
        compose.onNodeWithContentDescription("Tocar").performClick()
        assertEquals(Voicings.keys("C").first(), played)
    }

    @Test fun `Details with a capo - the field is of what sounds, not of the chart's shape`() {
        compose.setContent { StagebookTheme { ChordShapePopup("Em7", Instrument.ACOUSTIC_GUITAR, "{key: Em}\n{capo: 2}\n[Em7]um [G]dois", {}, {}) } }
        compose.onNodeWithText("Detalhes").performClick()
        compose.onNodeWithText("Detalhes de F#m7").assertExists()
        compose.onNodeWithText("No tom da música (F#m)").assertExists()
        compose.onNodeWithText("F#m7 é o i7 neste tom.").assertExists()
        compose.onNodeWithText("O diagrama é de Em7. Com capo na 2ª casa, soa F#m7.").assertExists()
    }

    @Test fun `the chord has the same name on both screens with a capo`() {
        compose.setContent { StagebookTheme { ChordShapePopup("G", Instrument.ACOUSTIC_GUITAR, "{key: G}\n{capo: 1}\n[G]um [C]dois", {}, {}) } }
        compose.onNodeWithText("O diagrama é de G. Com capo na 1ª casa, soa Ab.").assertExists()
        compose.onNodeWithText("Detalhes").performClick()
        compose.onNodeWithText("Detalhes de Ab").assertExists()
        compose.onNodeWithText("O diagrama é de G. Com capo na 1ª casa, soa Ab.").assertExists()
    }

    @Test fun `Details work on a song without a key`() {
        compose.setContent { StagebookTheme { ChordShapePopup("Em", Instrument.ACOUSTIC_GUITAR, "{capo: 2}\n[Em]um [Am]dois [B7]tres [Em]fim", {}, {}) } }
        compose.onNodeWithText("Detalhes").performClick()
        compose.onNodeWithText("No tom da música (F#m)").assertExists()
    }

    @Config(qualifiers = "pt-rBR-w640dp-h320dp-land")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun `Details scroll on a phone on its side`() {
        compose.setContent { StagebookTheme { ChordShapePopup("Em7", Instrument.ACOUSTIC_GUITAR, "{key: Em}\n{capo: 2}\n[Em7]um [G]dois", {}, {}) } }
        compose.onNodeWithText("Detalhes").performScrollTo().performClick()
        compose.onNodeWithText("O diagrama é de Em7. Com capo na 2ª casa, soa F#m7.").performScrollTo().assertIsDisplayed()
    }

    @Test fun `Details on the keyboard - the chord already is what sounds, it doesn't go up twice`() {
        compose.setContent { StagebookTheme { ChordShapePopup("F#m7", Instrument.KEYBOARD, "{key: Em}\n{capo: 2}\n[Em7]um [G]dois", {}, {}) } }
        compose.onNodeWithText("Detalhes").performClick()
        compose.onNodeWithText("Detalhes de F#m7").assertExists()
        compose.onNodeWithText("No tom da música (F#m)").assertExists()
    }

    @Test fun `the capo note comes before the harmonic field`() {
        compose.setContent { StagebookTheme { ChordShapePopup("Em7", Instrument.ACOUSTIC_GUITAR, "{key: Em}\n{capo: 2}\n[Em7]um [G]dois", {}, {}) } }
        compose.onNodeWithText("Detalhes").performClick()
        val note = compose.onNodeWithText("O diagrama é de Em7. Com capo na 2ª casa, soa F#m7.").getBoundsInRoot()
        assertTrue(note.bottom <= compose.onNodeWithText("No tom da música (F#m)").getBoundsInRoot().top)
        assertTrue(note.top - compose.onNodeWithText("Detalhes de F#m7").getBoundsInRoot().bottom < 16.dp)   // right under the title
        compose.onNodeWithContentDescription("Como funciona").performClick()
        compose.onNodeWithText("Os Detalhes mostram o que soa", substring = true).assertExists()
    }

    @Config(qualifiers = "pt-rBR-w412dp-h915dp-port")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)   // real text widths: the row has to wrap
    @Test fun `a chord left alone on the field's second row sits in the middle`() {
        compose.setContent { StagebookTheme { ChordShapePopup("Em7", Instrument.ACOUSTIC_GUITAR, "{key: Em}\n{capo: 2}\n[Em7]um [G]dois", {}, {}) } }
        compose.onNodeWithText("Detalhes").performClick()
        val alone = compose.onAllNodesWithText("E7")[0].getBoundsInRoot()
        assertEquals(206f, ((alone.left + alone.right) / 2).value, 2f)
    }

    @Test fun `with a capo the diagram starts at it - a shape too high keeps its place and shows the real fret`() {
        val open = Voicings.Shape(listOf(0, 2, 2, 0, 3, 0))
        assertEquals(Voicings.NeckView(1, fromCapo = true, label = 2), Voicings.neckView(open, 2, 5))
        assertEquals(Voicings.NeckView(1, fromCapo = false, label = null), Voicings.neckView(open, 0, 5))
        val secondFret = Voicings.Shape(listOf(-1, 2, 4, 4, 3, 2))
        assertEquals(Voicings.NeckView(1, fromCapo = true, label = 3), Voicings.neckView(secondFret, 3, 5))
        val high = Voicings.Shape(listOf(-1, 7, 9, 9, 8, 7))
        assertEquals(Voicings.NeckView(7, fromCapo = false, label = 9), Voicings.neckView(high, 2, 5))
        assertEquals(Voicings.NeckView(7, fromCapo = false, label = 7), Voicings.neckView(high, 0, 5))
    }

    @Test fun `diagram lines land on whole pixels`() {
        assertEquals(2f, br.app.stagebook.ui.components.crispWidth(2.25f), 0f)
        assertEquals(1f, br.app.stagebook.ui.components.crispWidth(0.3f), 0f)
        assertEquals(10.5f, br.app.stagebook.ui.components.crisp(10.7f, 1f), 0f)
        assertEquals(11f, br.app.stagebook.ui.components.crisp(10.7f, 2f), 0f)
    }

    private fun detailsWidth(): androidx.compose.ui.unit.Dp {
        compose.setContent { StagebookTheme { ChordShapePopup("Em7", Instrument.ACOUSTIC_GUITAR, "{key: Em}\n{capo: 2}\n[Em7]um [G]dois", {}, {}) } }
        compose.onNodeWithText("Detalhes").performScrollTo().performClick()
        compose.waitForIdle()
        return compose.onNodeWithTag("chord_window").getBoundsInRoot().let { it.right - it.left }
    }

    @Config(qualifiers = "pt-rBR-w412dp-h915dp-port")
    @Test fun `Details take the whole width on an upright phone`() = assertEquals(412f, detailsWidth().value, 1f)

    @Config(qualifiers = "pt-rBR-w915dp-h412dp-land")
    @Test fun `Details stay a window on a phone on its side`() = assertTrue(detailsWidth().value < 600f)

    @Config(qualifiers = "pt-rBR-w800dp-h1280dp-port")
    @Test fun `Details stay a window on an upright tablet`() = assertTrue(detailsWidth().value < 600f)

    @Config(qualifiers = "pt-rBR-w320dp-h640dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)   // real text widths: the default mode measures text too narrow to overflow
    @Test fun `Details on a narrow phone - long chords stay on one line`() {
        compose.setContent { StagebookTheme { ChordShapePopup("D#m7", Instrument.ACOUSTIC_GUITAR, "{key: C#}\n[C#]um [D#m7]dois", {}, {}) } }
        compose.onNodeWithText("Detalhes").performClick()
        val cells = compose.onAllNodesWithText("m7", substring = true).fetchSemanticsNodes()
        assertTrue(cells.size >= 4)
        cells.forEach { cell ->
            val layouts = ArrayList<androidx.compose.ui.text.TextLayoutResult>()
            cell.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!(layouts)
            if (layouts.single().layoutInput.text.text.length < 10) assertEquals(layouts.single().layoutInput.text.text, 1, layouts.single().lineCount)
        }
    }

    @Test fun `Details - degree in the song's key, the chord's field and where else it shows up - and back`() {
        compose.setContent { StagebookTheme { ChordShapePopup("C", Instrument.ACOUSTIC_GUITAR, "{key: G}\n[G]um [C]dois", {}, {}) } }
        compose.onNodeWithText("Detalhes").performClick()
        compose.onNodeWithText("Detalhes de C").assertExists()
        compose.onNodeWithText("No tom da música (G)").assertExists()
        compose.onNodeWithText("C é o IV neste tom.").assertExists()
        compose.onNodeWithText("Campo harmônico de C").assertExists()
        compose.onNodeWithText("V em F").assertExists()
        compose.onNodeWithContentDescription("Voltar ao diagrama").performClick()
        compose.onNodeWithContentDescription("Tocar").assertExists()
    }
}
