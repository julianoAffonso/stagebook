package com.juliano.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.juliano.stagebook.data.model.Look
import com.juliano.stagebook.ui.stage.StageScreen
import com.juliano.stagebook.ui.stage.StageSong
import com.juliano.stagebook.ui.stage.StageUiState
import com.juliano.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Metrônomo do palco (T33c): começa no BPM da música (sem BPM, 100), conta 1-2-3-4 e o ícone vira stop. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1100dp-h720dp-land-xhdpi", application = android.app.Application::class)
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
        var settings: Pair<Boolean, Boolean>? = null
        compose.setContent { StagebookTheme { StageScreen(state("linha"), 0, 0, { _, _ -> }, onMetronome = { c, s -> settings = c to s }) } }
        compose.mainClock.autoAdvance = false
        fun frames(n: Int) = repeat(n) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.onNodeWithContentDescription("Metrônomo").performClick()
        frames(3)
        compose.onNodeWithText("100 BPM").assertExists()
        compose.onNodeWithText("Som (desligado: só pisca)").performClick()   // a chave grava nos ajustes
        assertEquals(true to false, settings)
        compose.onNodeWithContentDescription("Começar o metrônomo").performClick()
        frames(3)
        compose.onNodeWithText("1").assertExists()                           // contagem
        compose.onNodeWithContentDescription("Parar o metrônomo").performClick()
        frames(3)
        compose.onNodeWithContentDescription("Metrônomo").assertExists()
    }
}
