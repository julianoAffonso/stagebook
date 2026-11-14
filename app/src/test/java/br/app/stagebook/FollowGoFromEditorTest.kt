package br.app.stagebook

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
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
import br.app.stagebook.navigation.FollowToStage
import br.app.stagebook.navigation.Home
import br.app.stagebook.navigation.SongEditor
import br.app.stagebook.navigation.Stage
import br.app.stagebook.ui.band.goNow
import br.app.stagebook.ui.components.FollowGate
import br.app.stagebook.ui.components.LocalFollowGate
import br.app.stagebook.ui.editor.SongEditorScreen
import br.app.stagebook.ui.editor.SongEditorUiState
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A follower in the song editor, the band moves on, and "Go" on the strip (device test v0.1.1123: it did nothing, and
 * with text typed the stage would have opened over the editor without asking). The real editor screen in the real
 * [FollowToStage]; the strip's "Go" is [goNow].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-port-xxhdpi", application = android.app.Application::class)
class FollowGoFromEditorTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var nav: NavHostController
    private val gate = FollowGate()
    private val songs = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private val following = MutableStateFlow<String?>("a")

    private fun start(unsaved: Boolean, underEditor: Boolean = true) {
        compose.setContent {
            StagebookTheme {
                CompositionLocalProvider(LocalFollowGate provides gate) {
                    nav = rememberNavController()
                    FollowToStage(nav, songs, following, gate, androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) })
                    NavHost(nav, startDestination = Home) {
                        composable<Home> { Text("home") }
                        composable<SongEditor> {
                            SongEditorScreen(SongEditorUiState(loading = false, title = "Hazard", text = "letra", unsaved = unsaved), {}, {}, {}, {}, {}, androidx.lifecycle.compose.dropUnlessResumed { nav.popBackStack() }, {})
                        }
                        composable<Stage> { entry -> Text("stage ${entry.toRoute<Stage>().songId}") }
                    }
                }
            }
        }
        compose.waitForIdle()
        if (underEditor) {
            compose.runOnIdle { nav.navigate(Stage(songId = "a")) }
            compose.waitForIdle()
        }
        compose.runOnIdle { nav.navigate(SongEditor(songId = "a")) }
        compose.waitForIdle()
    }

    private fun band(song: String) {
        following.value = song
        compose.runOnIdle { songs.tryEmit(song) }
        compose.waitForIdle()
    }

    private fun go() { compose.runOnIdle { gate.goNow() }; compose.waitForIdle() }

    @Test fun `Go with something typed asks first, and opens the band's song only after leaving without saving`() {
        start(unsaved = true)
        band("b")
        go()
        compose.onNodeWithText("Sair sem salvar?", substring = true).assertExists()
        compose.onAllNodesWithText("stage b").assertCountEquals(0)
        compose.onNodeWithText("Sair sem salvar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("stage b").assertExists()
        // the editor is gone: back from the song doesn't bring the discarded text back
        compose.runOnIdle { nav.popBackStack() }
        compose.waitForIdle()
        compose.onAllNodesWithText("Hazard").assertCountEquals(0)
    }

    @Test fun `Go with something typed and the question dismissed stays in the editor, still waiting`() {
        start(unsaved = true)
        band("b")
        go()
        compose.onNodeWithText("Cancelar").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("stage b").assertCountEquals(0)
        compose.onNodeWithText("Hazard", substring = true).assertExists()
        org.junit.Assert.assertEquals("b", gate.pending?.target)
    }

    @Test fun `Go with nothing typed leaves the editor and opens the band's song at once`() {
        start(unsaved = false)
        band("b")
        go()
        compose.onAllNodesWithText("Sair sem salvar?", substring = true).assertCountEquals(0)
        compose.onNodeWithText("stage b").assertExists()
        assertNull(gate.pending)
    }

    @Test fun `leaving the editor by Back opens the newest song of the band, not the one the stage had`() {
        start(unsaved = false)
        band("b")
        band("c")
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("stage c").assertExists()
        compose.onAllNodesWithText("stage b").assertCountEquals(0)
    }
}
