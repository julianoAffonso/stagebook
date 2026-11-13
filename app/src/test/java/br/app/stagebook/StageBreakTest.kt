package br.app.stagebook

import android.view.KeyEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import br.app.stagebook.data.model.Look
import br.app.stagebook.domain.PedalAction
import br.app.stagebook.ui.LocalPedalInput
import br.app.stagebook.ui.PedalInput
import br.app.stagebook.ui.stage.StageScreen
import br.app.stagebook.ui.stage.StageSong
import br.app.stagebook.ui.stage.StageUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A setlist's break on stage: past the end of the song before it comes the break screen, and only leaving it opens the
 * next song (which is when its MIDI, Now Playing and band message go out: they follow the song's position).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi", application = android.app.Application::class)
class StageBreakTest {
    @get:Rule val compose = createComposeRule()

    private val songs = listOf(
        StageSong("Primeira", "", "Uma linha da primeira", false, id = "s1"),
        StageSong("Segunda", "", "{key: G}\nUma linha da segunda", false, id = "s2"),
        StageSong("Terceira", "", "Uma linha da terceira", false, id = "s3"),
    )
    private var song by mutableIntStateOf(0)
    private var page by mutableIntStateOf(0)
    private var since by mutableStateOf<Long?>(null)
    private val pedal = PedalInput()

    private fun show(breaks: List<Int>, look: Look = Look(), start: Int = 0) {
        song = start
        compose.setContent {
            StagebookTheme {
                CompositionLocalProvider(LocalPedalInput provides pedal) {
                    StageScreen(
                        StageUiState(loading = false, songs = songs, look = look, live = true, breaks = breaks), song, page, { s, p -> song = s; page = p },
                        breakSince = since, onBreak = { on -> since = if (on) System.currentTimeMillis() else null },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun press(key: Int) {
        compose.runOnUiThread {
            pedal.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, key, 0))
            pedal.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_UP, key, 0))
        }
        compose.waitForIdle()
    }
    private fun forward() = press(KeyEvent.KEYCODE_PAGE_DOWN)
    private fun back() = press(KeyEvent.KEYCODE_PAGE_UP)

    @Test fun `the end of the song before a break says the break comes next`() {
        show(listOf(1))
        compose.onNodeWithText("Próxima: Intervalo").assertExists()
        compose.onNodeWithText("INTERVALO").assertDoesNotExist()
    }

    @Test fun `going on shows the break with the next song, and going on again opens it`() {
        show(listOf(1))
        forward()
        assertEquals("the song on stage is still the one before the break", 0, song)
        compose.onNodeWithText("INTERVALO").assertIsDisplayed()
        compose.onNodeWithText("00:00").assertIsDisplayed()
        compose.onNodeWithText("A seguir: Segunda · G").assertIsDisplayed()
        forward()
        assertEquals(1, song)
        assertEquals(null, since)
        compose.onNodeWithText("INTERVALO").assertDoesNotExist()
        compose.onNodeWithText("Uma linha da segunda", substring = true).assertIsDisplayed()
    }

    @Test fun `going back from the break returns to the song before it`() {
        show(listOf(1))
        forward()
        compose.onNodeWithText("INTERVALO").assertIsDisplayed()
        back()
        assertEquals(0, song)
        assertEquals(null, since)
        compose.onNodeWithText("INTERVALO").assertDoesNotExist()
        compose.onNodeWithText("Uma linha da primeira", substring = true).assertIsDisplayed()
    }

    @Test fun `a tap on the break goes on or back like on the lyrics`() {
        show(listOf(1))
        compose.onRoot().performTouchInput { click(Offset(width * 0.9f, height / 2f)) }   // right side: forward
        compose.waitForIdle()
        compose.onNodeWithText("INTERVALO").assertIsDisplayed()
        compose.onRoot().performTouchInput { click(Offset(width * 0.1f, height / 2f)) }   // left side: back to the song
        compose.waitForIdle()
        assertEquals(0, song)
        compose.onNodeWithText("INTERVALO").assertDoesNotExist()
        compose.onRoot().performTouchInput { click(Offset(width * 0.9f, height / 2f)) }
        compose.waitForIdle()
        compose.onRoot().performTouchInput { click(Offset(width * 0.9f, height / 2f)) }
        compose.waitForIdle()
        assertEquals(1, song)
    }

    @Test fun `the pedal set to next song stops at the break too`() {
        show(listOf(1), Look(pedalKeys = PedalAction.assign(emptyMap(), KeyEvent.KEYCODE_B, PedalAction.NEXT_SONG)))
        press(KeyEvent.KEYCODE_B)
        assertEquals(0, song)
        compose.onNodeWithText("INTERVALO").assertIsDisplayed()
        press(KeyEvent.KEYCODE_B)
        assertEquals(1, song)
        compose.onNodeWithText("INTERVALO").assertDoesNotExist()
    }

    @Test fun `a next song with no key shows only its name`() {
        show(listOf(2), start = 1)
        forward()
        compose.onNodeWithText("A seguir: Terceira").assertIsDisplayed()
    }

    @Test fun `with no break after the song the next one opens right away`() {
        show(listOf(2))
        compose.onNodeWithText("Próxima: Segunda").assertExists()
        forward()
        assertEquals(1, song)
        assertEquals(null, since)
    }

    @Test fun `a song opened right after a break shows no break first`() {
        show(listOf(1), start = 1)
        compose.onNodeWithText("INTERVALO").assertDoesNotExist()
        compose.onNodeWithText("Uma linha da segunda", substring = true).assertIsDisplayed()
    }
}
