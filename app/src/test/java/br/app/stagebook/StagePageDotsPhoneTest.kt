package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import br.app.stagebook.data.model.Look
import br.app.stagebook.ui.stage.StageScreen
import br.app.stagebook.ui.stage.StageSong
import br.app.stagebook.ui.stage.StageUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** On a phone the page numbers stay inside the screen, the last one whole (device test B-18). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class StagePageDotsPhoneTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `the page dots fit on a phone`() {
        val long = (1..200).joinToString("\n") { "Linha $it da letra" }
        compose.setContent { StagebookTheme { StageScreen(StageUiState(false, listOf(StageSong("Longa", "", long, false, id = "s1")), look = Look(fontSp = 51f)), 0, 0, { _, _ -> }) } }
        compose.waitForIdle()
        val width = compose.onRoot().fetchSemanticsNode().boundsInRoot.right
        val dots = (1..99).flatMap { n -> compose.onAllNodesWithText("$n").fetchSemanticsNodes().filter { it.boundsInRoot.top > 1500 } }
        assertTrue("sem bolinhas", dots.isNotEmpty())
        dots.forEach { assertTrue("passou da borda: ${it.boundsInRoot}", it.boundsInRoot.right <= width + 0.5f) }
    }
}
