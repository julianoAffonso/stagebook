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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
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
        // one sentence in two pieces: the name can be cut short, the key never is
        compose.onNode(androidx.compose.ui.test.hasText("A seguir: Segunda") and androidx.compose.ui.test.hasText(" · G")).assertIsDisplayed()
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

    // ---- nothing moves under the leader's break (the pedal still turned the page there, user 13/11) ----

    private val long = StageSong("Longa", "", (1..30).joinToString("\n\n") { v -> (1..4).joinToString("\n") { "longa estrofe $v linha $it" } }, false, id = "s1")
    private val keys = mapOf(
        KeyEvent.KEYCODE_B to br.app.stagebook.domain.PedalAction.NEXT_SONG,
        KeyEvent.KEYCODE_C to br.app.stagebook.domain.PedalAction.PREVIOUS_SONG,
        KeyEvent.KEYCODE_D to br.app.stagebook.domain.PedalAction.OPEN_SETLIST,
    )
    private var page by mutableIntStateOf(0)
    private val moves = mutableListOf<Pair<Int, Int>>()

    /** A stage of several pages (and, with [songs], several songs) under the leader's break. */
    private fun showLong(songs: List<StageSong> = listOf(long), start: Int = 0, startPage: Int = 1, setlist: br.app.stagebook.ui.stage.StageSetlist? = null) {
        index = start
        page = startPage
        compose.setContent {
            StagebookTheme {
                CompositionLocalProvider(LocalPedalInput provides pedal) {
                    StageScreen(
                        StageUiState(
                            loading = false, songs = songs, live = true, followNext = "Segunda", followBreak = rest, setlist = setlist,
                            look = br.app.stagebook.data.model.Look(fontSp = 34f, pedalKeys = keys),
                        ),
                        index, page, { s, p -> moves += s to p; index = s; page = p },
                        onBreak = { asked++ },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun swipe(forward: Boolean) {
        compose.onRoot().performTouchInput {
            if (forward) swipeLeft(startX = centerX + width / 4f, endX = centerX - width / 4f)
            else swipeRight(startX = centerX - width / 4f, endX = centerX + width / 4f)
        }
        compose.waitForIdle()
    }

    @Test fun `under the leader's break the pedal, a tap and a swipe turn no page`() {
        rest = FollowBreak(System.currentTimeMillis(), "Segunda", "G")
        showLong()
        press(KeyEvent.KEYCODE_PAGE_DOWN)
        press(KeyEvent.KEYCODE_PAGE_UP)
        compose.onRoot().performTouchInput { click(Offset(width * 0.9f, height / 2f)) }
        compose.onRoot().performTouchInput { click(Offset(width * 0.1f, height / 2f)) }
        swipe(forward = true)
        swipe(forward = false)
        assertEquals("nothing asked the stage to move", emptyList<Pair<Int, Int>>(), moves)
        assertEquals(0 to 1, index to page)
        compose.onNodeWithText("INTERVALO").assertIsDisplayed()
    }

    @Test fun `under the leader's break the pedal changes no song either`() {
        rest = FollowBreak(System.currentTimeMillis(), "Segunda", "G")
        val three = listOf(long.copy(id = "a"), long.copy(id = "b"), long.copy(id = "c"))
        showLong(three, start = 1)
        press(KeyEvent.KEYCODE_B)   // next song
        press(KeyEvent.KEYCODE_C)   // previous song
        press(KeyEvent.KEYCODE_PAGE_DOWN)
        press(KeyEvent.KEYCODE_PAGE_UP)
        assertEquals(emptyList<Pair<Int, Int>>(), moves)
        assertEquals(1 to 1, index to page)
        assertEquals(0, asked)
    }

    @Test fun `the pedal still opens the setlist over the leader's break, and a song tapped there opens nothing`() {
        rest = FollowBreak(System.currentTimeMillis(), "Segunda", "G")
        val three = listOf(long.copy(id = "a", title = "Um"), long.copy(id = "b", title = "Dois"), long.copy(id = "c", title = "Tres"))
        showLong(three, start = 1, setlist = br.app.stagebook.ui.stage.StageSetlist("Show de sábado"))
        press(KeyEvent.KEYCODE_D)
        compose.onNodeWithTag("stage_setlist").assertIsDisplayed()
        compose.onNode(androidx.compose.ui.test.hasText("Tres") and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("stage_setlist"))).performClick()
        compose.waitForIdle()
        assertEquals(emptyList<Pair<Int, Int>>(), moves)
        assertEquals(1 to 1, index to page)
    }

    @Test fun `when the leader leaves the break the pedal turns the page again`() {
        rest = FollowBreak(System.currentTimeMillis(), "Segunda", "G")
        showLong()
        press(KeyEvent.KEYCODE_PAGE_DOWN)
        assertEquals(0 to 1, index to page)
        rest = null
        compose.waitForIdle()
        compose.onNodeWithText("INTERVALO").assertDoesNotExist()
        press(KeyEvent.KEYCODE_PAGE_DOWN)
        assertEquals(0 to 2, index to page)
    }
}
