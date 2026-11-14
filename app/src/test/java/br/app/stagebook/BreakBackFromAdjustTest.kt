package br.app.stagebook

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import br.app.stagebook.domain.BreakTimer
import br.app.stagebook.navigation.Home
import br.app.stagebook.navigation.SongEditor
import br.app.stagebook.navigation.Stage
import br.app.stagebook.navigation.openSettingsFrom
import br.app.stagebook.ui.components.SetlistBreakRow
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Back from an adjust screen opened from the break goes to the break: the stage is still in the back stack with its own
 * view model (and its saved state, where the break is), so nothing has to be rebuilt. Checked with a stand-in view
 * model that counts instances, on the app's real routes. Settings used to take the stage's place instead; now it goes
 * over it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-port-xxhdpi", application = android.app.Application::class)
class BreakBackFromAdjustTest {
    @get:Rule val compose = createComposeRule()

    class Marker : ViewModel() { val id = ++count; companion object { var count = 0 } }

    private lateinit var nav: NavHostController
    private var stageId = 0

    private fun start() {
        compose.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = Home) {
                composable<Home> { Text("home") }
                composable<SongEditor> { Text("editor") }
                composable<Stage> { stageId = viewModel<Marker>().id; Text("stage") }
            }
        }
        compose.waitForIdle()
    }

    private fun go(action: () -> Unit) { compose.runOnIdle(action); compose.waitForIdle() }

    @Test fun `back from the editor opened over the stage returns to the same stage`() {
        start()
        go { nav.navigate(Stage(setId = "s", start = 0)) }
        val first = stageId
        go { nav.navigate(SongEditor(songId = "x")) }
        compose.onNodeWithText("editor").assertExists()
        go { nav.popBackStack() }
        compose.onNodeWithText("stage").assertExists()
        assertEquals("the same stage, not another one from zero", first, stageId)
    }

    @Test fun `Settings opened from the stage goes over it and back returns to the same stage`() {
        start()
        go { nav.navigate(Stage(setId = "s", start = 0)) }
        val first = stageId
        go { nav.openSettingsFrom() }
        compose.onNodeWithText("home").assertExists()
        go { nav.popBackStack() }
        compose.onNodeWithText("stage").assertExists()
        assertEquals(first, stageId)
    }

    @Test fun `Settings from another screen still takes the place of what is above Home`() {
        start()
        go { nav.navigate(SongEditor()) }
        go { nav.openSettingsFrom() }
        compose.onNodeWithText("home").assertExists()
        assertEquals(Home::class.qualifiedName, nav.currentBackStackEntry?.destination?.route?.substringBefore('/')?.substringBefore('?'))
    }

    @Test fun `a stage opened by tapping a song is another stage`() {
        start()
        go { nav.navigate(Stage(setId = "s", start = 0)) }
        val first = stageId
        go { nav.popBackStack() }
        go { nav.navigate(Stage(setId = "s", start = 0)) }
        assertNotEquals(first, stageId)
    }
}

/** The setlist's break row while the break is open on the stage: its time, running or paused, and a tap opens it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-port-xxhdpi", application = android.app.Application::class)
class SetlistOpenBreakRowTest {
    @get:Rule val compose = createComposeRule()

    private fun row(timer: BreakTimer.Timer?, onOpen: () -> Unit = {}) = compose.setContent {
        StagebookTheme { SetlistBreakRow(handle = null, onRemove = null, timer = timer, onOpen = onOpen) }
    }

    @Test fun `a running break shows its time and opens on tap`() {
        var opened = 0
        row(BreakTimer.Timer("d", System.currentTimeMillis() - 750_000, 0L, open = true)) { opened++ }
        compose.onNodeWithText("INTERVALO · 12:30").assertExists()
        compose.onAllNodesWithText("Pausado").assertCountEquals(0)
        compose.onNodeWithText("INTERVALO · 12:30").performClick()
        assertEquals(1, opened)
    }

    @Test fun `a paused break shows what it counted, with the pause icon`() {
        row(BreakTimer.Timer("d", 0L, 750_000L, open = true))
        compose.onNodeWithText("INTERVALO · 12:30").assertExists()
        compose.onNodeWithContentDescription("Pausado").assertExists()
    }

    @Test fun `without an open break the row is as before`() {
        row(null)
        compose.onNodeWithText("INTERVALO").assertExists()
    }
}
