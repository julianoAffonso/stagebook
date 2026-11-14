package br.app.stagebook

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import br.app.stagebook.domain.FollowWait
import br.app.stagebook.domain.SetlistBreaks
import br.app.stagebook.navigation.FollowToStage
import br.app.stagebook.navigation.Home
import br.app.stagebook.navigation.SetlistDetail
import br.app.stagebook.navigation.Stage
import br.app.stagebook.ui.band.goNow
import br.app.stagebook.ui.components.FollowGate
import br.app.stagebook.ui.components.LocalFollowGate
import br.app.stagebook.ui.components.LocalFollowHold
import br.app.stagebook.ui.setlists.SetItemUi
import br.app.stagebook.ui.setlists.SetlistDetailScreen
import br.app.stagebook.ui.setlists.SetlistDetailUiState
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
 * A follower on the "Edit setlist" screen (device test v0.1.1159): the band changes songs and the screen is only looking
 * at it (no window, no drag): it took the follower to the stage with no strip and no "Go", and the break added a moment
 * before was gone on reopening. The screen is an action under way: it holds the follower the whole time it is open
 * (like the song editor), the strip says so, "Go" asks first only when something is not saved, and leaving the screen
 * by any way opens the newest song. The real [SetlistDetailScreen] in the real [FollowToStage], wired as the nav host
 * wires them (the same hold state on [LocalFollowHold]); the setlist lives in a state here, standing in for the library.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi", application = android.app.Application::class)
class FollowHoldInSetlistEditTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var nav: NavHostController
    private val gate = FollowGate()
    private val songs = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private val following = MutableStateFlow<String?>("a")
    private var breaks by mutableStateOf(listOf(2))   // "the library": what the edit has changed so far

    private fun start() {
        compose.setContent {
            StagebookTheme {
                val hold = remember { mutableStateOf(false) }
                CompositionLocalProvider(LocalFollowGate provides gate, LocalFollowHold provides hold) {
                    nav = rememberNavController()
                    FollowToStage(nav, songs, following, gate, hold)
                    NavHost(nav, startDestination = Home) {
                        composable<Home> { Text("home") }
                        composable<SetlistDetail> {
                            val items = ('a'..'c').map { SetItemUi("$it", "$it", "Música $it", "", false) }
                            SetlistDetailScreen(
                                SetlistDetailUiState(loading = false, name = "Show", items = items, breaks = breaks), null,
                                androidx.lifecycle.compose.dropUnlessResumed { nav.popBackStack() }, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {},
                                onAddBreak = { breaks = SetlistBreaks.add(breaks, 3) },
                            )
                        }
                        composable<Stage> { entry -> Text("stage ${entry.toRoute<Stage>().songId}") }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { nav.navigate(Stage(songId = "a")) }
        compose.waitForIdle()
        compose.runOnIdle { nav.navigate(SetlistDetail("s")) }
        compose.waitForIdle()
    }

    private fun band(song: String) {
        following.value = song
        compose.runOnIdle { songs.tryEmit(song) }
        compose.waitForIdle()
    }

    private fun go() { compose.runOnIdle { gate.goNow() }; compose.waitForIdle() }

    @Test fun `with the edit open and nothing being changed the band's song waits, with the strip's reason`() {
        start()
        band("b")
        compose.onNodeWithText("Editar setlist").assertExists()
        compose.onAllNodesWithText("stage b").assertCountEquals(0)
        assertEquals("b", gate.pending?.target)
        assertEquals(FollowWait.SCREEN, gate.pending?.reason)
    }

    @Test fun `after adding a break the band's song still waits, and the break is still there on reopening`() {
        start()
        compose.onNodeWithText("Intervalo").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("3 músicas · 2 intervalos", substring = true).assertExists()
        band("b")
        compose.onAllNodesWithText("stage b").assertCountEquals(0)
        compose.onNodeWithText("3 músicas · 2 intervalos", substring = true).assertExists()
        assertEquals(listOf(1, 2), breaks)
        go()
        compose.onNodeWithText("stage b").assertExists()
        // reopening the edit: nothing was lost
        compose.runOnIdle { nav.navigate(SetlistDetail("s")) }
        compose.waitForIdle()
        compose.onNodeWithText("3 músicas · 2 intervalos", substring = true).assertExists()
    }

    @Test fun `Go with nothing unsaved leaves the edit and opens what the band shows now, without asking`() {
        start()
        band("b")
        band("c")
        go()
        compose.onAllNodesWithText("Sair sem salvar?", substring = true).assertCountEquals(0)
        compose.onNodeWithText("stage c").assertExists()
        compose.onAllNodesWithText("stage a").assertCountEquals(0)
        assertNull(gate.pending)
        // the stage on top is the only one: back from it goes to where the edit was left, not to a second stage
        compose.runOnIdle { nav.popBackStack() }
        compose.waitForIdle()
        compose.onNodeWithText("home").assertExists()
    }

    @Test fun `Go with a rename open asks first, and only leaving without saving opens the song`() {
        start()
        compose.onNodeWithContentDescription("Mais opções").performClick()
        compose.onNodeWithText("Renomear", substring = true).performClick()
        compose.waitForIdle()
        band("b")
        go()
        compose.onNodeWithText("Sair sem salvar?", substring = true).assertExists()
        compose.onAllNodesWithText("stage b").assertCountEquals(0)
        compose.onNodeWithText("Sair sem salvar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("stage b").assertExists()
        assertNull(gate.pending)
    }

    @Test fun `Go with a rename open and the question dismissed stays on the edit, still waiting`() {
        start()
        compose.onNodeWithContentDescription("Mais opções").performClick()
        compose.onNodeWithText("Renomear", substring = true).performClick()
        compose.waitForIdle()
        band("b")
        go()
        compose.onAllNodesWithText("Cancelar")[0].performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("stage b").assertCountEquals(0)
        assertEquals("b", gate.pending?.target)
    }

    @Test fun `leaving the edit by Done opens the newest song of the band`() {
        start()
        band("b")
        band("c")
        compose.onNodeWithText("Pronto").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("stage c").assertExists()
        compose.onAllNodesWithText("stage b").assertCountEquals(0)
    }

    @Test fun `leaving the edit by the X opens the newest song of the band`() {
        start()
        band("b")
        compose.onNodeWithContentDescription("Fechar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("stage b").assertExists()
    }

    @Test fun `leaving the edit with the band already gone from it opens nothing`() {
        start()
        band("b")
        following.value = null
        compose.waitForIdle()
        compose.onNodeWithText("Pronto").performClick()
        compose.waitForIdle()
        // nothing opens: the stage that was under the edit is still the one that shows
        compose.onNodeWithText("stage a").assertExists()
        compose.onAllNodesWithText("stage b").assertCountEquals(0)
    }
}
