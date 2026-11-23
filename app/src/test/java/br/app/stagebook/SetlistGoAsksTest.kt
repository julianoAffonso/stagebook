package br.app.stagebook

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.app.stagebook.ui.components.FollowGate
import br.app.stagebook.ui.components.LocalFollowGate
import br.app.stagebook.ui.setlists.SetItemUi
import br.app.stagebook.ui.setlists.SetlistDetailScreen
import br.app.stagebook.ui.setlists.SetlistDetailUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Following the band with a setlist being edited: "Go" on the strip asks Save, Discard or Keep editing before leaving,
 * as closing the menu does. It used to leave with the half-done edit applied and no question (user, 16/11).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class SetlistGoAsksTest {
    @get:Rule val compose = createComposeRule()
    private val gate = FollowGate()
    private var left = 0
    private var went = 0
    private val state = SetlistDetailUiState(
        loading = false, name = "Show",
        items = listOf(SetItemUi("a", "a", "Música a", "", false), SetItemUi("b", "b", "Música b", "", false)), breaks = emptyList(),
    )

    private fun show(editing: Boolean) = compose.setContent {
        StagebookTheme {
            CompositionLocalProvider(LocalFollowGate provides gate) {
                SetlistDetailScreen(state, null, { left++ }, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, startEditing = editing)
            }
        }
    }

    private fun go() { compose.runOnIdle { gate.leaveGuard!!.invoke { went++ } }; compose.waitForIdle() }
    private val question = "Salvar as alterações em “Show”?"

    @Test fun `Go with the setlist being edited asks, and Keep editing stays`() {
        show(editing = true)
        go()
        compose.onNodeWithText(question, substring = true).assertExists()
        assertEquals("nothing left yet", 0 to 0, left to went)
        compose.onNodeWithText("Continuar editando").performClick()
        compose.waitForIdle()
        assertEquals(0 to 0, left to went)
        compose.onAllNodesWithText(question, substring = true).assertCountEquals(0)
    }

    @Test fun `Save goes to the band's song`() {
        show(editing = true)
        go()
        compose.onNodeWithText("Salvar").performClick()
        compose.waitForIdle()
        assertEquals(1 to 1, left to went)
    }

    @Test fun `Discard goes too`() {
        show(editing = true)
        go()
        compose.onNodeWithText("Descartar").performClick()
        compose.waitForIdle()
        assertEquals(1 to 1, left to went)
    }

    @Test fun `a setlist only being looked at goes without a question`() {
        show(editing = false)
        go()
        compose.onAllNodesWithText(question, substring = true).assertCountEquals(0)
        assertEquals(1 to 1, left to went)
    }
}
