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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Stage: the mixer opens over the stage, so the backing track keeps playing while adjusting (user, 31/10). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1100dp-h720dp-land-xhdpi", application = android.app.Application::class)
class StageMixerTest {
    @get:Rule val compose = createComposeRule()

    private fun stage(track: String, playerMode: Boolean = true) {
        val song = StageSong("Música", "", "[G]linha", false, id = "s1", track = track)
        compose.setContent {
            StagebookTheme { StageScreen(StageUiState(false, listOf(song), look = Look(playerMode = playerMode)), 0, 0, { _, _ -> }) }
        }
    }

    @Test fun `the mixer button opens the mixer over the stage`() {
        stage(track = "s1.ogg")
        compose.onNodeWithContentDescription("Abrir o mixer").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ver os níveis ao vivo").assertExists()
    }

    @Test fun `no mixer button on a song without a backing track`() {
        stage(track = "")
        compose.onNodeWithContentDescription("Abrir o mixer").assertDoesNotExist()
    }

    @Test fun `no mixer button with the player off`() {
        stage(track = "s1.ogg", playerMode = false)
        compose.onNodeWithContentDescription("Abrir o mixer").assertDoesNotExist()
    }
}
