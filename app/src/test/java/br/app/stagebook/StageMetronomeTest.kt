package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

/** Metrônomo do palco (T33c): começa no BPM da música (sem BPM, 100), conta 1-2-3-4 e o ícone vira stop. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1100dp-h720dp-land-xhdpi", application = android.app.Application::class)
class StageMetronomeTest {
    @get:Rule val compose = createComposeRule()

    private fun state(text: String) = StageUiState(false, listOf(StageSong("Música", "", text, false)), look = Look())

    @Test fun `sem BPM começa em 100 - com BPM começa nele`() {
        compose.setContent { StagebookTheme { StageScreen(state("{tempo: 120}\nlinha"), 0, 0, { _, _ -> }) } }
        compose.onNodeWithContentDescription("Metrônomo").performClick()
        compose.onNodeWithText("120 BPM").assertExists()
        compose.onNodeWithContentDescription("Mais rápido").performClick()
        compose.onNodeWithText("121 BPM").assertExists()
    }

    @Test fun `play conta 1-2-3-4 e o ícone vira stop - stop para`() {
        compose.setContent { StagebookTheme { StageScreen(state("linha"), 0, 0, { _, _ -> }) } }
        compose.mainClock.autoAdvance = false
        fun frames(n: Int) = repeat(n) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.onNodeWithContentDescription("Metrônomo").performClick()
        frames(3)
        compose.onNodeWithText("100 BPM").assertExists()
        compose.onNodeWithContentDescription("Começar o metrônomo").performClick()
        frames(3)
        compose.onNodeWithText("1").assertExists()                           // contagem
        compose.onNodeWithContentDescription("Parar o metrônomo").performClick()
        frames(3)
        compose.onNodeWithContentDescription("Metrônomo").assertExists()
    }
}
