package br.app.stagebook

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import br.app.stagebook.data.model.Look
import br.app.stagebook.domain.PedalAction
import br.app.stagebook.domain.SetlistShow.State
import br.app.stagebook.ui.LocalPedalInput
import br.app.stagebook.ui.PedalInput
import br.app.stagebook.ui.stage.StageRowFacts
import br.app.stagebook.ui.stage.StageScreen
import br.app.stagebook.ui.stage.StageSetlist
import br.app.stagebook.ui.stage.StageSong
import br.app.stagebook.ui.stage.StageUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The stage during the show: the break silences the metronome, and the setlist panel takes the pedal's page turns. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi", application = android.app.Application::class)
class StageMidShowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val long = (1..120).joinToString("\n") { "Linha $it da letra" }
    private val songs = listOf(
        StageSong("Primeira", "", "Uma linha", false, id = "s1"),
        StageSong("Segunda", "", long, false, id = "s2"),
        StageSong("Terceira", "", "Outra linha", false, id = "s3"),
    )
    private val setlist = StageSetlist("Show", listOf(State.PLAYED, State.NOW, State.NEXT), listOf("G", "Em", "").map { StageRowFacts(it) })
    private var song by mutableIntStateOf(1)
    private var page by mutableIntStateOf(0)
    private var since by mutableStateOf<Long?>(null)
    private val pedal = PedalInput()
    private val clicks = mutableListOf<Int?>()

    private fun show(breaks: List<Int> = listOf(2), keys: Map<Int, PedalAction> = emptyMap()) {
        compose.setContent {
            StagebookTheme {
                CompositionLocalProvider(LocalPedalInput provides pedal) {
                    StageScreen(
                        StageUiState(loading = false, songs = songs, live = true, breaks = breaks, setlist = setlist, look = Look(pedalKeys = keys)),
                        song, page, { s, p -> song = s; page = p }, onBack = {},
                        breakSince = since, onBreak = { on -> since = if (on) System.currentTimeMillis() else null },
                        onMetronomeSound = { clicks += it },
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

    @Test fun `going to the break turns the metronome off`() {
        show()
        compose.onNodeWithContentDescription("Metrônomo").performClick()
        compose.onNodeWithContentDescription("Começar o metrônomo").performClick()
        compose.waitForIdle()
        assertNotNull("the click sounds", clicks.lastOrNull())
        while (since == null && page < 20) press(KeyEvent.KEYCODE_PAGE_DOWN)
        assertNotNull("on the break", since)
        compose.waitForIdle()
        assertNull("no click on the break", clicks.last())
    }
}
