package com.juliano.stagebook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.juliano.stagebook.data.model.Look
import com.juliano.stagebook.data.model.StageMode
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

/** Rolagem automática (T15): o ▶ liga, tocar na tela pausa sem mudar de música, e −/+ mudam a velocidade. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1100dp-h720dp-land-xhdpi", application = android.app.Application::class)
class StageAutoScrollTest {
    @get:Rule val compose = createComposeRule()

    private val long = (1..40).joinToString("\n") { "Linha $it da letra de exemplo" }
    private val state = StageUiState(false, listOf(StageSong("Longa", "", long, false), StageSong("Outra", "", "x", false)), look = Look(stageMode = StageMode.SCROLL))

    @Test fun `play rola sozinho - tocar na tela pausa e fica na mesma música`() {
        var song by mutableIntStateOf(0)
        var level = 0
        compose.setContent {
            StagebookTheme {
                var page by androidx.compose.runtime.remember { mutableIntStateOf(0) }
                StageScreen(state, song, page, { s, p -> song = s; page = p }, onAutoScroll = { level = it })
            }
        }
        // relógio parado (quadro a quadro): esperando a tela, o teste andaria o tempo até a rolagem chegar ao fim
        compose.mainClock.autoAdvance = false
        fun frames(n: Int) = repeat(n) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        fun open() { compose.onNodeWithContentDescription("Rolagem automática").performClick(); frames(3) }
        // play na janelinha: ela fecha e a letra rola; tocar na tela pausa, sem mudar de música
        open()
        compose.onNodeWithContentDescription("Rolar sozinho").performClick()
        frames(60)
        compose.onRoot().performTouchInput { click(centerRight.copy(x = centerRight.x - 50f)) }
        frames(10)
        assertEquals(0, song)
        open()
        compose.onNodeWithContentDescription("Rolar sozinho").assertExists()
        // de novo: rolando, o ícone ao lado do relógio vira pause e para na hora
        compose.onNodeWithContentDescription("Rolar sozinho").performClick()
        frames(30)
        compose.onNodeWithContentDescription("Rolagem automática").assertDoesNotExist()
        compose.onNodeWithContentDescription("Pausar a rolagem").performClick()
        frames(10)
        compose.onNodeWithContentDescription("Rolagem automática").assertExists()
        // o × fecha sem mudar nada
        open()
        compose.onNodeWithContentDescription("Fechar").performClick()
        frames(5)
        compose.onNodeWithContentDescription("Rolar sozinho").assertDoesNotExist()
        open()
        compose.onNodeWithContentDescription("Mais rápido").performClick()
        assertEquals(Look.DEFAULT_AUTO_SCROLL + 1, level)
    }
}
