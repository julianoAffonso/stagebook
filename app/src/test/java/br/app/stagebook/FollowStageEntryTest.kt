package br.app.stagebook

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Like the stage's view model: the song is read from the route once, when the view model is made. */
class RouteOnceViewModel(handle: SavedStateHandle) : ViewModel() {
    val songId = handle.toRoute<Stage>().songId
}

/**
 * Device test v0.1.1139: the follower's stage stayed on the song before after a window closed ("Close") or after "Go"
 * from the editor, though the navigation had gone to the band's song. The stage that was open took the new arguments
 * but kept its entry, and so its view model, which had read the song once. The stage here gets its song from a view
 * model of its entry (as the real one does), not from the entry's route as a plain screen would.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-port-xxhdpi", application = android.app.Application::class)
class FollowStageEntryTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var nav: NavHostController
    private val gate = FollowGate()
    private val songs = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private val following = MutableStateFlow<String?>("a")
    private var text = "letra"

    private fun start() {
        compose.setContent {
            StagebookTheme {
                CompositionLocalProvider(LocalFollowGate provides gate) {
                    nav = rememberNavController()
                    FollowToStage(nav, songs, following, gate, androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) })
                    NavHost(nav, startDestination = Home) {
                        composable<Home> { Text("home") }
                        composable<SongEditor> {
                            SongEditorScreen(SongEditorUiState(loading = false, title = "Hazard", text = text, unsaved = false), {}, {}, {}, {}, {}, androidx.lifecycle.compose.dropUnlessResumed { nav.popBackStack() }, {})
                        }
                        composable<Stage> { Text("stage ${viewModel<RouteOnceViewModel>().songId}") }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { nav.navigate(Stage(songId = "a")) }
        compose.waitForIdle()
    }

    private fun band(song: String) {
        following.value = song
        compose.runOnIdle { songs.tryEmit(song) }
        compose.waitForIdle()
    }

    @Test fun `a window open on the stage, the band moves, closing it shows the band's song`() {
        start()
        compose.runOnIdle { gate.menus++ }
        compose.waitForIdle()
        band("b")
        compose.onNodeWithText("stage a").assertExists()
        compose.runOnIdle { gate.menus-- }
        compose.waitForIdle()
        compose.onNodeWithText("stage b").assertExists()
        compose.onAllNodesWithText("stage a").assertCountEquals(0)
    }

    @Test fun `Go from the editor opens the band's song, not the stage underneath`() {
        start()
        compose.runOnIdle { nav.navigate(SongEditor(songId = "a")) }
        compose.waitForIdle()
        band("b")
        compose.runOnIdle { gate.goNow() }
        compose.waitForIdle()
        compose.onNodeWithText("stage b").assertExists()
        compose.onAllNodesWithText("stage a").assertCountEquals(0)
    }
}
