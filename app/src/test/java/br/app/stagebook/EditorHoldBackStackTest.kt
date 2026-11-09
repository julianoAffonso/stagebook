package br.app.stagebook

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import br.app.stagebook.domain.HoldPlace
import br.app.stagebook.domain.PendingFile
import br.app.stagebook.navigation.Home
import br.app.stagebook.navigation.SongEditor
import br.app.stagebook.navigation.Stage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The hold glue of StagebookNavHost asks the back stack "is a SongEditor anywhere?" with the typed lookup
 * (`getBackStackEntry<SongEditor>()`, which throws when there's none). StagebookNavHost itself needs Hilt view models,
 * so this pins that lookup on the app's real routes: an editor below the stage still holds, and leaving it releases.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class EditorHoldBackStackTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var nav: NavHostController

    private fun editing() = runCatching { nav.getBackStackEntry<SongEditor>() }.isSuccess
    private fun place() = HoldPlace.of(onStage = false, editing = editing())

    private fun start() {
        compose.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = Home) {
                composable<Home> { Text("home") }
                composable<SongEditor> { Text("editor") }
                composable<Stage> { Text("stage") }
            }
        }
        compose.waitForIdle()
    }

    private fun go(action: () -> Unit) {
        compose.runOnIdle(action)
        compose.waitForIdle()
    }

    @Test fun `an editor anywhere in the back stack holds, and popping it releases`() {
        start()
        assertEquals(HoldPlace.NONE, place())
        go { nav.navigate(SongEditor(songId = "s1", cursor = 4)) }
        assertEquals(HoldPlace.EDITOR, place())
        // the editor opened from the stage, then another screen on top: still in the stack, still holds
        go { nav.navigate(Stage(songId = "s1")) }
        assertEquals(HoldPlace.EDITOR, place())
        go { nav.popBackStack() }
        assertEquals(HoldPlace.EDITOR, place())
        go { nav.popBackStack() }
        assertEquals(HoldPlace.NONE, place())
    }

    @Test fun `a file that arrived while editing opens after leaving the editor`() {
        start()
        val file = PendingFile<String>()
        go { nav.navigate(SongEditor()) }
        assertEquals(PendingFile.Arrival.HELD, file.arrive("show.stagebook", place().holds))
        go { nav.popBackStack() }
        assertEquals("show.stagebook", if (!place().holds) file.release() else null)
    }
}
