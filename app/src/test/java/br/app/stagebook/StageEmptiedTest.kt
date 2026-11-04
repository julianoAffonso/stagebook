package br.app.stagebook

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import br.app.stagebook.ui.stage.leaveWhenEmptied
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Deleting the song on stage goes back to where it was opened from, not to a stage with nothing to show. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class StageEmptiedTest {
    @get:Rule val compose = createComposeRule()

    private var loading by mutableStateOf(false)
    private var showing by mutableStateOf(true)
    private lateinit var nav: NavHostController

    private fun app() = compose.setContent {
        nav = rememberNavController()
        NavHost(nav, startDestination = "list") {
            composable("list") { Text("list") }
            composable("stage") {
                // the same guarded way back as the real stage: it does nothing until the screen is resumed
                val back = dropUnlessResumed { nav.popBackStack() }
                val emptied = leaveWhenEmptied(loading, showing, back)
                Text(if (emptied) "blank" else "stage")
            }
            composable("editor") { Text("editor") }
        }
    }

    private fun go(route: String) {
        compose.runOnUiThread { nav.navigate(route) }
        compose.waitForIdle()
    }

    @Test
    fun `deleting in the editor skips the stage on the way back`() {
        app()
        go("stage")
        go("editor")
        showing = false
        compose.runOnUiThread { nav.popBackStack() }
        compose.waitForIdle()
        compose.onNodeWithText("list").assertExists()
    }

    @Test
    fun `a song deleted from the computer closes the open stage`() {
        app()
        go("stage")
        showing = false
        compose.waitForIdle()
        compose.onNodeWithText("list").assertExists()
    }

    @Test
    fun `a stage that opened with nothing stays to explain why`() {
        showing = false
        app()
        go("stage")
        compose.onNodeWithText("stage").assertExists()
    }

    @Test
    fun `the library still loading doesn't count as deleted`() {
        app()
        go("stage")
        loading = true
        showing = false
        compose.waitForIdle()
        compose.onNodeWithText("stage").assertExists()
    }
}
