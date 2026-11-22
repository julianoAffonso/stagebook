package br.app.stagebook

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import br.app.stagebook.data.model.Look
import br.app.stagebook.ui.stage.StageScreen
import br.app.stagebook.ui.stage.StageSong
import br.app.stagebook.ui.stage.StageUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The stage's metronome is one thing for a screen reader (V16-7: "Metrônomo" showed twice in a device dump; not reproduced, 22/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class StageMetronomeNodeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `the metronome is read once`() {
        compose.setContent { StagebookTheme { StageScreen(StageUiState(false, listOf(StageSong("Uma", "", "[G]Letra", false, id = "s1")), look = Look()), 0, 0, { _, _ -> }, onBack = {}, onMenu = {}) } }
        compose.waitForIdle()
        val described = compose.onAllNodesWithContentDescription("Metrônomo").fetchSemanticsNodes().size
        val written = compose.onAllNodesWithText("Metrônomo").fetchSemanticsNodes().size
        assertEquals(1, described + written)
    }
}
