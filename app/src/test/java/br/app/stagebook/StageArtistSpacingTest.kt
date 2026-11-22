package br.app.stagebook

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
import org.robolectric.annotation.GraphicsMode

/** On a phone the artist line belongs to the song's name: closer to the title than to the lyrics (user, 22/11). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class StageArtistSpacingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `the artist is closer to the title than to the lyrics`() {
        compose.setContent {
            StagebookTheme {
                StageScreen(StageUiState(false, listOf(StageSong("Tempo", "Banda Teste · 1990", "Primeira linha da letra\nSegunda linha", false, id = "s1")), look = Look()), 0, 0, { _, _ -> }, onBack = {}, onMenu = {})
            }
        }
        compose.waitForIdle()
        val title = compose.onNodeWithText("Tempo").getUnclippedBoundsInRoot()
        val artist = compose.onNodeWithText("Banda Teste · 1990").getUnclippedBoundsInRoot()
        val lyrics = compose.onNodeWithText("Primeira linha da letra", substring = true).getUnclippedBoundsInRoot()
        val above = artist.top - title.bottom
        val below = lyrics.top - artist.bottom
        assertTrue("title→artist $above should be less than artist→lyrics $below", above < below)
    }
}
