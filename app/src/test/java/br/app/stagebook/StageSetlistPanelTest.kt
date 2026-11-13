package br.app.stagebook

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import br.app.stagebook.data.model.Look
import br.app.stagebook.domain.PedalAction
import br.app.stagebook.domain.SetlistShow.State
import br.app.stagebook.ui.LocalPedalInput
import br.app.stagebook.ui.PedalInput
import br.app.stagebook.ui.stage.StageScreen
import br.app.stagebook.ui.stage.StageSetlist
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
 * The setlist over the stage: a handle at the left edge, only on a stage opened from a setlist, opens a panel with the
 * songs and breaks. A tap on a song goes to it; anything else only closes the panel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi", application = android.app.Application::class)
class StageSetlistPanelTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    // long enough for a second page, so "back to the first page" is a real change
    private val long = (1..120).joinToString("\n") { "Linha $it da letra" }
    private val songs = listOf(
        StageSong("Primeira", "Banda um · 1985", "Uma linha da primeira", false, id = "s1", artist = "Banda um"),
        StageSong("Segunda", "Banda dois · 1999", long, false, id = "s2", artist = "Banda dois"),
        StageSong("Terceira", "", "Uma linha da terceira", false, id = "s3"),
        StageSong("Quarta", "", "Uma linha da quarta", false, id = "s4"),
    )
    // the first played, the second on stage, the third next; a break before the third
    private val setlist = StageSetlist("Show de sábado", listOf(State.PLAYED, State.NOW, State.NEXT, State.NONE), listOf("G", "Em", "", "A"))
    private var song by mutableIntStateOf(1)
    private var page by mutableIntStateOf(0)
    private var since by mutableStateOf<Long?>(null)
    private val positions = mutableListOf<Pair<Int, Int>>()
    private val pedal = PedalInput()
    private var restarts = 0

    private fun show(
        state: StageUiState = StageUiState(loading = false, songs = songs, live = true, breaks = listOf(2), setlist = setlist),
        start: Int = 1,
        startPage: Int = 0,
    ) {
        song = start
        page = startPage
        compose.setContent {
            StagebookTheme {
                CompositionLocalProvider(LocalPedalInput provides pedal) {
                    StageScreen(
                        state, song, page, { s, p -> positions += s to p; song = s; page = p }, onBack = {},
                        breakSince = since, onBreak = { on -> since = if (on) System.currentTimeMillis() else null },
                        onRestartShow = { restarts++ },
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

    /** Forward to the end of the second song, whose next row is the break. */
    private fun toBreak() {
        while (since == null && page < 20) press(KeyEvent.KEYCODE_PAGE_DOWN)
        assertEquals("still the song before the break", 1, song)
    }

    /** Where one text of the panel is drawn (a row's texts are read together, so each one is looked up on its own). */
    private fun part(text: String) = compose.onNode(hasText(text) and inPanel, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private val inPanel = hasAnyAncestor(hasTestTag("stage_setlist"))
    private fun panelText(text: String) = compose.onNode(hasText(text) and inPanel)
    private fun handle() = compose.onNodeWithContentDescription("Abrir o setlist")
    private fun open() { handle().performClick(); compose.waitForIdle() }
    private fun panel() = compose.onNodeWithTag("stage_setlist")

    @Test fun `a single song from the library has no handle and no panel`() {
        show(StageUiState(loading = false, songs = songs.take(1), look = Look(pedalKeys = PedalAction.assign(emptyMap(), KeyEvent.KEYCODE_B, PedalAction.OPEN_SETLIST))), start = 0)
        handle().assertDoesNotExist()
        panel().assertDoesNotExist()
        // and the pedal set to open the setlist opens nothing
        press(KeyEvent.KEYCODE_B)
        panel().assertDoesNotExist()
    }

    @Test fun `a stage opened from a setlist has the handle, and the panel only after a tap on it`() {
        show()
        handle().assertIsDisplayed()
        panel().assertDoesNotExist()
        panelText("Show de sábado").assertDoesNotExist()
        open()
        panel().assertIsDisplayed()
    }

    @Test fun `the panel shows the setlist's name, the position and every song and break in order`() {
        show()
        open()
        panelText("Show de sábado").assertIsDisplayed()
        panelText("2 de 4").assertIsDisplayed()
        listOf("Primeira", "Segunda", "Terceira", "Quarta").forEach { panelText(it).assertIsDisplayed() }
        listOf("1", "2", "3", "4").forEach { panelText(it).assertIsDisplayed() }
        panelText("Banda um").assertIsDisplayed()
        panelText("Banda um · 1985").assertDoesNotExist()   // the year is the header's, not the row's
        panelText("INTERVALO").assertIsDisplayed()
        listOf("G", "Em", "A").forEach { panelText(it).assertIsDisplayed() }
        // in the setlist's order, the break between the second and the third
        val tops = listOf("Primeira", "Segunda", "INTERVALO", "Terceira", "Quarta").map { part(it).top }
        assertEquals(tops.sorted(), tops)
    }

    @Test fun `played, now and next are written on their songs`() {
        show()
        open()
        panelText("TOCADA").assertIsDisplayed()
        panelText("AGORA").assertIsDisplayed()
        panelText("PRÓX.").assertIsDisplayed()
        // each on its own song's row, at the row's start edge
        listOf("TOCADA" to "Primeira", "AGORA" to "Segunda", "PRÓX." to "Terceira").forEach { (state, title) ->
            val row = panelText(title).fetchSemanticsNode().boundsInRoot
            val strip = part(state)
            assertEquals("$state on $title: $strip in $row", true, strip.center.y in row.top..row.bottom && strip.left >= row.left && strip.right <= part(title).left)
        }
    }

    @Test fun `only the states the songs have are written`() {
        show(StageUiState(loading = false, songs = songs, live = true, setlist = setlist.copy(states = listOf(State.NONE, State.NONE, State.NONE, State.NOW))), start = 3)
        open()
        panelText("AGORA").assertIsDisplayed()
        panelText("PRÓX.").assertDoesNotExist()
        panelText("TOCADA").assertDoesNotExist()
        panelText("INTERVALO").assertDoesNotExist()
    }

    @Test fun `tapping a song goes to its first page and closes the panel`() {
        show(startPage = 1)
        open()
        panelText("Quarta").performClick()
        compose.waitForIdle()
        assertEquals(3, song)
        assertEquals(0, page)
        assertEquals(listOf(3 to 0), positions)
        assertEquals("no break on the way", null, since)
        panel().assertDoesNotExist()
        compose.onNodeWithText("Uma linha da quarta", substring = true).assertIsDisplayed()
    }

    @Test fun `tapping the song on stage goes back to its first page`() {
        show(startPage = 1)
        open()
        panelText("Segunda").performClick()
        compose.waitForIdle()
        assertEquals(1, song)
        assertEquals(0, page)
        panel().assertDoesNotExist()
    }

    @Test fun `tapping the song on stage, already on its first page, only closes`() {
        show()
        open()
        panelText("Segunda").performClick()
        compose.waitForIdle()
        assertEquals(emptyList<Pair<Int, Int>>(), positions)
        panel().assertDoesNotExist()
        // and the stage still turns pages afterwards
        press(KeyEvent.KEYCODE_PAGE_DOWN)
        assertEquals(1 to 1, song to page)
    }

    @Test fun `a tap outside the panel closes it and turns no page`() {
        show()
        open()
        // on the right half of the stage, where a tap goes forward
        compose.onRoot().performTouchInput { click(Offset(width * 0.9f, height / 2f)) }
        compose.waitForIdle()
        panel().assertDoesNotExist()
        assertEquals(emptyList<Pair<Int, Int>>(), positions)
        // closed, the same tap turns the page again
        compose.onRoot().performTouchInput { click(Offset(width * 0.9f, height / 2f)) }
        compose.waitForIdle()
        assertEquals(listOf(1 to 1), positions)
    }

    @Test fun `a swipe on the stage under the open panel turns no page`() {
        show()
        open()
        compose.onNodeWithTag("stage_setlist_scrim").performTouchInput { swipeLeft(startX = width * 0.9f, endX = width * 0.6f) }
        compose.waitForIdle()
        assertEquals(emptyList<Pair<Int, Int>>(), positions)
    }

    @Test fun `the system back closes the panel first, without leaving the stage or changing song`() {
        show()
        open()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        panel().assertDoesNotExist()
        assertEquals(emptyList<Pair<Int, Int>>(), positions)
        compose.onNodeWithText("Sair do palco?", substring = true).assertDoesNotExist()
        // closed, back is the stage's again: in a show it asks before leaving
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText(compose.activity.getString(R.string.stage_exit_confirm)).assertIsDisplayed()
    }

    @Test fun `dragging the handle to the right opens the panel, and dragging the panel back closes it`() {
        show()
        handle().performTouchInput { swipeRight(startX = centerX, endX = centerX + 400f) }
        compose.waitForIdle()
        panel().assertIsDisplayed()
        panelText("Show de sábado").assertIsDisplayed()
        panel().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        panel().assertDoesNotExist()
        assertEquals(emptyList<Pair<Int, Int>>(), positions)
    }

    @Test fun `the handle rides on the open panel and closes it`() {
        show()
        open()
        handle().assertDoesNotExist()
        compose.onNodeWithContentDescription("Fechar o setlist").assertIsDisplayed().performClick()
        compose.waitForIdle()
        panel().assertDoesNotExist()
        handle().assertIsDisplayed()
    }

    @Test fun `the pedal set to open the setlist opens it and closes it`() {
        show(StageUiState(loading = false, songs = songs, live = true, setlist = setlist, look = Look(pedalKeys = PedalAction.assign(emptyMap(), KeyEvent.KEYCODE_B, PedalAction.OPEN_SETLIST))))
        press(KeyEvent.KEYCODE_B)
        panel().assertIsDisplayed()
        panelText("Show de sábado").assertIsDisplayed()
        press(KeyEvent.KEYCODE_B)
        panel().assertDoesNotExist()
        assertEquals(emptyList<Pair<Int, Int>>(), positions)
    }

    @Test fun `on the break the handle is there, and a song tapped leaves the break for it`() {
        show()
        toBreak()
        compose.onNodeWithText("00:00").assertIsDisplayed()   // the break's own screen
        positions.clear()
        handle().assertIsDisplayed()
        open()
        panelText("Quarta").performClick()
        compose.waitForIdle()
        assertEquals(null, since)
        assertEquals(3 to 0, song to page)
        compose.onNodeWithText("00:00").assertDoesNotExist()
        compose.onNodeWithText("Uma linha da quarta", substring = true).assertIsDisplayed()
    }

    @Test fun `going back from the break through the panel leaves the break too`() {
        show()
        toBreak()
        open()
        panelText("Primeira").performClick()
        compose.waitForIdle()
        assertEquals(null, since)
        assertEquals(0 to 0, song to page)
        assertEquals("only a set after the break counts from zero", 0, restarts)
    }

    @Test fun `closing the panel on the break keeps the break`() {
        show()
        toBreak()
        val started = since
        open()
        compose.onNodeWithTag("stage_setlist_scrim").performTouchInput { click(Offset(width * 0.9f, height / 2f)) }
        compose.waitForIdle()
        panel().assertDoesNotExist()
        assertEquals(started, since)
        compose.onNodeWithText("00:00").assertIsDisplayed()
    }

    @Test fun `essential mode keeps the setlist over the stage`() {
        show(StageUiState(loading = false, songs = songs, live = true, setlist = setlist, look = Look(bandOn = false, internetOn = false, extrasOn = false, playerMode = false)))
        open()
        panelText("Show de sábado").assertIsDisplayed()
        panelText("Quarta").performClick()
        compose.waitForIdle()
        assertEquals(3, song)
    }

    @Test fun `the song on stage is in view when a long setlist opens`() {
        val many = (1..40).map { StageSong("Música $it", "", "Uma linha", false, id = "m$it") }
        show(StageUiState(loading = false, songs = many, live = true, setlist = StageSetlist("Longo")), start = 30)
        open()
        panelText("Música 31").assertIsDisplayed()
        panelText("31 de 40").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "pt-rBR-w320dp-h640dp-port-xhdpi")
    fun `on a narrow phone the panel leaves some of the stage in sight`() {
        show()
        open()
        panel().assertWidthIsEqualTo(272.dp)   // 85% of the screen
    }

    @Test @Config(qualifiers = "pt-rBR-w412dp-h915dp-port-xxhdpi")
    fun `on a phone the panel is as wide as on a tablet`() {
        show()
        open()
        panel().assertWidthIsEqualTo(304.dp)
        panelText("Segunda").assertIsDisplayed()
        panelText("Em").assertIsDisplayed()
    }
}
