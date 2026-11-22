package br.app.stagebook

import android.view.KeyEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import br.app.stagebook.data.model.ExternalShow
import br.app.stagebook.data.model.Look
import br.app.stagebook.domain.MirrorLines
import br.app.stagebook.ui.LocalPedalInput
import br.app.stagebook.ui.PedalInput
import br.app.stagebook.ui.settings.SettingsActions
import br.app.stagebook.ui.settings.SettingsScreen
import br.app.stagebook.ui.settings.SettingsSection
import br.app.stagebook.ui.settings.SettingsUiState
import br.app.stagebook.ui.stage.ExternalStageContent
import br.app.stagebook.ui.stage.MirrorFrame
import br.app.stagebook.ui.stage.StageMirror
import br.app.stagebook.ui.stage.StageScreen
import br.app.stagebook.ui.stage.StageSong
import br.app.stagebook.ui.stage.StageUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** A second screen follows the stage's page and shows what the person chose (user, 22/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w800dp-h1280dp", application = android.app.Application::class)
class ExternalScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `the song lines behind the stage lines, skipping lines the stage made`() {
        val sources = listOf(-1, -1, 0, 1, 1, 2, 4, 5)
        assertEquals(0..2, MirrorLines.sourceRange(sources, listOf(0..5)))
        assertEquals(1..5, MirrorLines.sourceRange(sources, listOf(3..4, 6..7)))
        assertNull(MirrorLines.sourceRange(sources, listOf(0..1)))
        assertNull(MirrorLines.sourceRange(sources, listOf(IntRange.EMPTY)))
    }

    private val song = "[Verse]\n[G]Primeira linha\n[D]Segunda linha\n\n[Chorus]\n[C]Terceira linha\n[Em]Quarta linha"

    private fun show(frame: MirrorFrame?, look: Look) {
        compose.setContent { StagebookTheme { ExternalStageContent(frame, look, { File(it) }, Modifier.fillMaxSize()) } }
    }

    @Test fun `only the part the stage shows, with chords when chosen`() {
        show(MirrorFrame("s", "Canção", text = song, lines = 4..6), Look(externalShow = ExternalShow.CHORDS))
        compose.onNodeWithText("Terceira linha", substring = true).assertExists()
        compose.onNodeWithText("Primeira linha", substring = true).assertDoesNotExist()
    }

    @Test fun `lyrics only leaves the chords out`() {
        var look by mutableStateOf(Look(externalShow = ExternalShow.CHORDS))
        compose.setContent { StagebookTheme { ExternalStageContent(MirrorFrame("s", "Canção", text = "[G]Primeira linha"), look, { File(it) }) } }
        // the chord marks the letter it sits over: with chords the drawn text carries it, without them it doesn't
        fun chordsDrawn() = compose.onNodeWithText("Primeira linha", substring = true).fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.Text].sumOf { it.getStringAnnotations(br.app.stagebook.ui.stage.CHORD_TAG, 0, it.length).size }
        assertEquals(1, chordsDrawn())
        compose.runOnUiThread { look = look.copy(externalShow = ExternalShow.LYRICS) }
        compose.waitForIdle()
        assertEquals(0, chordsDrawn())
    }

    @Test fun `a PDF song in a text mode shows its name, a page with no song lines too`() {
        var frame by mutableStateOf(MirrorFrame("p", "Partitura", "Compositor", pdf = "x.pdf"))
        compose.setContent { StagebookTheme { ExternalStageContent(frame, Look(externalShow = ExternalShow.LYRICS), { File(it) }) } }
        compose.onNodeWithText("Partitura").assertExists()
        compose.onNodeWithText("Compositor").assertExists()
        compose.runOnUiThread { frame = MirrorFrame("s", "Só acordes", text = "[G] [D]", lines = 0..0) }
        compose.onNodeWithText("Só acordes").assertExists()
    }

    @Test fun `the stage leaves the page it shows in the mirror, and the next page after the pedal`() {
        val long = (1..300).joinToString("\n") { "Linha número $it da canção" }
        val mirror = StageMirror()
        val pedal = PedalInput()
        var index by mutableIntStateOf(0)
        var page by mutableIntStateOf(0)
        compose.setContent {
            StagebookTheme {
                CompositionLocalProvider(LocalPedalInput provides pedal) {
                    StageScreen(
                        StageUiState(loading = false, songs = listOf(StageSong("Longa", "Banda", long, false, id = "l"))),
                        index, page, { i, p -> index = i; page = p }, mirror = mirror,
                    )
                }
            }
        }
        compose.waitForIdle()
        val first = mirror.frame!!
        assertEquals("l", first.songId)
        assertEquals(0, first.lines!!.first)
        assertTrue("one page holds part of the song: ${first.lines}", first.lines.last < 299)
        compose.runOnUiThread {
            pedal.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, 0))
            pedal.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PAGE_DOWN, 0))
        }
        compose.waitForIdle()
        val second = mirror.frame!!.lines!!
        assertNotEquals(first.lines, second)
        assertTrue(second.first > first.lines.first)
    }

    @Test fun `Settings offers it beside Now Playing, and it goes with the extras`() {
        var chosen: ExternalShow? = null
        var state by mutableStateOf(SettingsUiState(loading = false))
        compose.setContent {
            StagebookTheme {
                SettingsScreen(state, SettingsActions(onExternalShow = { chosen = it }), Modifier.fillMaxSize(), section = SettingsSection.BAND, onLeave = {}, onMenu = {})
            }
        }
        compose.onNodeWithText("Tela externa").performScrollTo().assertExists()
        compose.onNodeWithText("Letra e cifra").performScrollTo().performClick()
        compose.onNodeWithText("Só letra").performClick()
        assertEquals(ExternalShow.LYRICS, chosen)
        compose.runOnUiThread { state = state.copy(look = state.look.copy(extrasOn = false)) }
        compose.onNodeWithText("Tela externa").assertDoesNotExist()
    }
}
