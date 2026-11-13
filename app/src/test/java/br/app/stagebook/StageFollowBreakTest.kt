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
import br.app.stagebook.ui.LocalPedalInput
import br.app.stagebook.ui.PedalInput
import br.app.stagebook.ui.stage.FollowBreak
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
 * Following the band, the leader's break covers the follower's stage: the same screen as a setlist's own break, which
 * only the leader's next message takes away.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi", application = android.app.Application::class)
class StageFollowBreakTest {
    @get:Rule val compose = createComposeRule()

    private val song = StageSong("Primeira", "", "Uma linha da primeira", false, id = "s1")
    private var rest by mutableStateOf<FollowBreak?>(null)
    private var index by mutableIntStateOf(0)
    private var asked = 0
    private val pedal = PedalInput()

    private fun show() {
        compose.setContent {
            StagebookTheme {
                CompositionLocalProvider(LocalPedalInput provides pedal) {
                    StageScreen(
                        StageUiState(loading = false, songs = listOf(song), live = true, followNext = "Segunda", followBreak = rest), index, 0, { s, _ -> index = s },
                        onBreak = { asked++ },
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

    @Test fun `the leader's break shows with its time and the next song, and goes away with the leader`() {
        rest = FollowBreak(System.currentTimeMillis() - 125_000, "Segunda", "G")
        show()
        compose.onNodeWithText("INTERVALO").assertIsDisplayed()
        compose.onNodeWithText("02:05").assertIsDisplayed()
        compose.onNodeWithText("A seguir: Segunda · G").assertIsDisplayed()
        rest = null
        compose.waitForIdle()
        compose.onNodeWithText("INTERVALO").assertDoesNotExist()
        compose.onNodeWithText("Uma linha da primeira", substring = true).assertIsDisplayed()
    }

    @Test fun `without the next song here, the leader's title shows with no key`() {
        rest = FollowBreak(System.currentTimeMillis(), "Segunda")
        show()
        compose.onNodeWithText("A seguir: Segunda").assertIsDisplayed()
    }

    @Test fun `a follower doesn't leave the break by tapping or with the pedal`() {
        rest = FollowBreak(System.currentTimeMillis(), "Segunda", "G")
        show()
        compose.onRoot().performTouchInput { click(Offset(width * 0.9f, height / 2f)) }
        compose.onRoot().performTouchInput { click(Offset(width * 0.1f, height / 2f)) }
        press(KeyEvent.KEYCODE_PAGE_DOWN)
        press(KeyEvent.KEYCODE_PAGE_UP)
        compose.waitForIdle()
        compose.onNodeWithText("INTERVALO").assertIsDisplayed()
        assertEquals(0, index)
        assertEquals("a follower's stage starts no break of its own", 0, asked)
    }
}
