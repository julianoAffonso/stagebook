package br.app.stagebook

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import br.app.stagebook.data.model.Instrument
import br.app.stagebook.data.model.Look
import br.app.stagebook.data.model.StageMode
import br.app.stagebook.domain.FollowWait
import br.app.stagebook.domain.MixerConfig
import br.app.stagebook.navigation.FollowToStage
import br.app.stagebook.navigation.Home
import br.app.stagebook.navigation.Stage
import br.app.stagebook.ui.components.FollowGate
import br.app.stagebook.ui.components.FollowPending
import br.app.stagebook.ui.components.LocalFollowGate
import br.app.stagebook.ui.stage.ChordShapePopup
import br.app.stagebook.ui.stage.StageMixerSheet
import br.app.stagebook.ui.stage.StageScreen
import br.app.stagebook.ui.stage.StageSong
import br.app.stagebook.ui.stage.StageUiState
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A follower on the stage with one of the stage's own windows open (metronome, chord, mixer) isn't taken to the leader's
 * other song: it waits, the strip says so, and the newest song opens when the window closes ("Go" opens it at once).
 * Before, the stage swapped songs under the open window (bb07e8c5 left it on purpose; the rule is now the app's).
 * Like FollowWaitsForMenuTest this drives FollowToStage (StagebookNavHost needs Hilt), with the real stage and windows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi", application = android.app.Application::class)
class StageFollowWaitsTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var nav: NavHostController
    private val gate = FollowGate()
    private val band = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private val following = MutableStateFlow<String?>("a")
    private val songs = listOf(
        StageSong("Primeira", "", "Letra da primeira", false, id = "a"),
        StageSong("Segunda", "", "Letra da segunda", false, id = "b"),
        StageSong("Terceira", "", "Letra da terceira", false, id = "c"),
    )

    /** The stage on song "a", the real screen, with [extra] drawn over it as the stage's own window. */
    private fun stage(ui: StageUiState = StageUiState(loading = false, songs = songs), extra: @androidx.compose.runtime.Composable () -> Unit = {}) {
        compose.setContent {
            StagebookTheme {
                CompositionLocalProvider(LocalFollowGate provides gate) {
                    nav = rememberNavController()
                    FollowToStage(nav, band, following, gate, androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) })
                    NavHost(nav, startDestination = Stage(songId = "a")) {
                        composable<Home> { Text("home") }
                        composable<Stage> { entry ->
                            val id = entry.toRoute<Stage>().songId
                            StageScreen(ui.copy(songs = ui.songs.ifEmpty { songs }), songs.indexOfFirst { it.id == id }.coerceAtLeast(0), 0, { _, _ -> })
                            extra()
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun leader(song: String) {
        following.value = song
        compose.runOnIdle { band.tryEmit(song) }
        compose.waitForIdle()
    }

    private fun openMetronome() {
        compose.onNodeWithContentDescription("Metrônomo").performClick()
        compose.onNodeWithContentDescription("Mais devagar").assertExists()   // the window is up
    }

    @Test fun `with the metronome window open the leader's song waits, then opens when it closes`() {
        stage()
        openMetronome()
        leader("b")
        compose.onNodeWithText("Letra da primeira", substring = true).assertExists()
        compose.onAllNodesWithText("Letra da segunda", substring = true).assertCountEquals(0)
        assertEquals(FollowPending("b", FollowWait.MENU), gate.pending)
        compose.onNodeWithContentDescription("Fechar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Letra da segunda", substring = true).assertExists()
        assertNull(gate.pending)
    }

    @Test fun `only the newest song opens`() {
        stage()
        openMetronome()
        leader("b")
        leader("c")
        assertEquals("c", gate.pending?.target)
        compose.onNodeWithContentDescription("Fechar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Letra da terceira", substring = true).assertExists()
        compose.onAllNodesWithText("Letra da segunda", substring = true).assertCountEquals(0)
    }

    @Test fun `Go opens the song at once, with the window still open`() {
        stage()
        openMetronome()
        leader("b")
        compose.onNodeWithText("Letra da primeira", substring = true).assertExists()   // waiting, not opened
        assertEquals("b", gate.pending?.target)
        compose.runOnIdle { gate.go() }
        compose.waitForIdle()
        compose.onNodeWithText("Letra da segunda", substring = true).assertExists()
        assertNull(gate.pending)
    }

    @Test fun `with no window open the song changes at once, as before`() {
        stage()
        leader("b")
        compose.onNodeWithText("Letra da segunda", substring = true).assertExists()
        assertNull(gate.pending)
    }

    @Test fun `a window opened and closed before the leader moves holds nothing`() {
        stage()
        openMetronome()
        compose.onNodeWithContentDescription("Fechar").performClick()
        compose.waitForIdle()
        leader("b")
        compose.onNodeWithText("Letra da segunda", substring = true).assertExists()
    }

    // ---- the rest of the stage's menus and windows, opened for real from the screen ----

    /** The leader moves to "b" with the window up: the song waits (the stage still shows "a"); closing it opens "b". */
    private fun assertHoldsThenOpens(close: () -> Unit) {
        leader("b")
        assertEquals(FollowPending("b", FollowWait.MENU), gate.pending)
        compose.onAllNodesWithText("Letra da segunda", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Letra da primeira", substring = true).assertExists()
        close()
        compose.waitForIdle()
        compose.onNodeWithText("Letra da segunda", substring = true).assertExists()
        assertNull(gate.pending)
    }

    @Test fun `the track button's menu holds the song too`() {
        val withTrack = songs.map { it.copy(track = "${it.id}.ogg") }
        stage(StageUiState(loading = false, songs = withTrack, look = Look(fontSp = 34f, playerMode = true)))
        compose.onNodeWithContentDescription("Tocar o backing track").performTouchInput { longClick() }
        compose.onNodeWithText("Trocar o backing track").assertExists()   // the menu is up
        // a menu closes by choosing an item (changing the track does nothing here)
        assertHoldsThenOpens { compose.onNodeWithText("Trocar o backing track").performClick() }
    }

    @Test fun `the auto-scroll window holds the song too`() {
        val long = (1..40).joinToString("\n") { "Linha $it" }
        val scroll = songs.map { it.copy(text = it.text + "\n" + long) }
        stage(StageUiState(loading = false, songs = scroll, look = Look(stageMode = StageMode.SCROLL)))
        compose.onNodeWithContentDescription("Rolagem automática").performClick()
        compose.onNodeWithContentDescription("Rolar sozinho").assertExists()   // the window is up
        assertHoldsThenOpens { compose.onNodeWithContentDescription("Fechar").performClick() }
    }

    @Test fun `the key and capo window holds the song too`() {
        val keyed = songs.map { it.copy(text = "{key: G}\n[G]" + it.text) }
        stage(StageUiState(loading = false, songs = keyed, look = Look(chords = true)))
        compose.onNodeWithContentDescription("Tom: G", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Tom e capo").assertExists()   // the window is up
        assertHoldsThenOpens { compose.onNodeWithContentDescription("Fechar").performClick() }
    }

    @Test fun `the show length window holds the song too`() {
        stage()
        compose.onNode(androidx.compose.ui.test.hasContentDescription("Hora", substring = true)).performTouchInput { longClick() }
        compose.onNodeWithContentDescription("Meia hora a mais").assertExists()   // the window is up
        assertHoldsThenOpens { compose.onNodeWithContentDescription("Fechar").performClick() }
    }

    @Test fun `the chord window holds the song too`() {
        var open by androidx.compose.runtime.mutableStateOf(true)
        stage(extra = { if (open) ChordShapePopup("G", Instrument.ACOUSTIC_GUITAR, "[G]linha", {}, { open = false }) })
        leader("b")
        assertEquals(FollowWait.MENU, gate.pending?.reason)
        compose.onAllNodesWithText("Letra da segunda", substring = true).assertCountEquals(0)
        compose.runOnIdle { open = false }
        compose.waitForIdle()
        compose.onNodeWithText("Letra da segunda", substring = true).assertExists()
    }

    @Test fun `the mixer sheet holds the song too`() {
        var open by androidx.compose.runtime.mutableStateOf(true)
        stage(extra = { if (open) StageMixerSheet(MixerConfig(), own = false, onMixer = {}, onOwn = {}, onGeneral = {}, onDismiss = { open = false }) })
        leader("b")
        assertEquals(FollowWait.MENU, gate.pending?.reason)
        compose.onAllNodesWithText("Letra da segunda", substring = true).assertCountEquals(0)
        compose.runOnIdle { open = false }
        compose.waitForIdle()
        compose.onNodeWithText("Letra da segunda", substring = true).assertExists()
    }
}
