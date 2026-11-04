package br.app.stagebook

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import br.app.stagebook.navigation.Home
import br.app.stagebook.navigation.SetlistDetail
import br.app.stagebook.navigation.SongEditor
import br.app.stagebook.navigation.Stage
import br.app.stagebook.navigation.showSaved
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Saving opens the song on stage, and the stage's back respects where it all started. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SaveToStageTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var nav: NavHostController

    private fun app() = compose.setContent {
        nav = rememberNavController()
        NavHost(nav, startDestination = Home) {
            composable<Home> { Text("home") }
            composable<SongEditor> { Text("editor") }
            composable<SetlistDetail> { Text("setlist") }
            composable<Stage> { Text("stage") }
        }
    }

    private fun act(step: () -> Unit) {
        compose.runOnUiThread(step)
        compose.waitForIdle()
    }

    private fun stage() = nav.currentBackStackEntry!!.toRoute<Stage>()

    @Test
    fun `from the song list the stage opens and back returns to the list`() {
        app()
        act { nav.navigate(SongEditor(songId = "a")) }
        act { nav.showSaved("a") }
        assertEquals("a", stage().songId)
        act { nav.popBackStack() }
        assertTrue(nav.currentDestination!!.hasRoute<Home>())
    }

    @Test
    fun `a new song gets the id it was saved with`() {
        app()
        act { nav.navigate(SongEditor()) }
        act { nav.showSaved("new") }
        assertEquals("new", stage().songId)
    }

    @Test
    fun `from a setlist's stage it returns to that stage and back returns to the setlist`() {
        app()
        act { nav.navigate(SetlistDetail("s")) }
        act { nav.navigate(Stage(setId = "s", start = 2)) }
        act { nav.navigate(SongEditor(songId = "a")) }
        act { nav.showSaved("a") }
        assertEquals("s", stage().setId)
        assertEquals(2, stage().start)
        act { nav.popBackStack() }
        assertTrue(nav.currentDestination!!.hasRoute<SetlistDetail>())
    }

    @Test
    fun `from a single song's stage it doesn't stack a second stage`() {
        app()
        act { nav.navigate(Stage(songId = "a")) }
        act { nav.navigate(SongEditor(songId = "a")) }
        act { nav.showSaved("a") }
        act { nav.popBackStack() }
        assertTrue(nav.currentDestination!!.hasRoute<Home>())
    }
}
