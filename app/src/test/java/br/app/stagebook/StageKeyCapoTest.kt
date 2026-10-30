package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.app.stagebook.data.model.Instrument
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

/** Stage: a tap on "Key G" or "Capo 2" opens − and + for both, without leaving the stage. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1100dp-h720dp-land-xhdpi", application = android.app.Application::class)
class StageKeyCapoTest {
    @get:Rule val compose = createComposeRule()

    private val song = StageSong("Música", "", "{key: G}\n{capo: 2}\n[G]linha [C]outra", false, id = "s1")

    @Test fun `tapping the key opens key and capo controls for the open song`() {
        val transposed = mutableListOf<Pair<String, Int>>()
        val capos = mutableListOf<Pair<String, Int>>()
        compose.setContent {
            StagebookTheme {
                StageScreen(
                    StageUiState(false, listOf(song)), 0, 0, { _, _ -> },
                    onTranspose = { id, s -> transposed += id to s },
                    onCapo = { id, c -> capos += id to c },
                )
            }
        }
        compose.onNodeWithText("Tom G", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Subir meio tom").performClick()
        compose.onNodeWithContentDescription("Capo uma casa acima").performClick()
        assertEquals(listOf("s1" to 1), transposed)
        assertEquals(listOf("s1" to 3), capos)
    }

    @Test fun `keyboard - only the key, no capo controls`() {
        compose.setContent { StagebookTheme { StageScreen(StageUiState(false, listOf(song), look = Look(instrument = Instrument.KEYBOARD, keyboardCapoHintSeen = true)), 0, 0, { _, _ -> }) } }
        compose.onNodeWithText("Tom A", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Subir meio tom").assertExists()
        compose.onNodeWithContentDescription("Capo uma casa acima").assertDoesNotExist()
    }
}
