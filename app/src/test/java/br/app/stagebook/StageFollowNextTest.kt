package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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

/** Following the band, the stage holds one song: the end of it shows the leader's next song (user, 02/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi", application = android.app.Application::class)
class StageFollowNextTest {
    @get:Rule val compose = createComposeRule()

    private val song = listOf(StageSong("Atual", "", "Uma linha", false, id = "s1"))

    @Test fun `the follower sees the leader's next song`() {
        compose.setContent { StagebookTheme { StageScreen(StageUiState(loading = false, songs = song, look = Look(), followNext = "Seguinte"), 0, 0, { _, _ -> }) } }
        compose.onNodeWithText("Próxima: Seguinte").assertExists()
    }

    @Test fun `on the leader's last song the follower sees the end of the setlist`() {
        compose.setContent { StagebookTheme { StageScreen(StageUiState(loading = false, songs = song, look = Look(), followEnd = true), 0, 0, { _, _ -> }) } }
        compose.onNodeWithText("Fim do setlist").assertExists()
    }
}
