package br.app.stagebook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import br.app.stagebook.data.model.ChordListPlace
import br.app.stagebook.data.model.Look
import br.app.stagebook.data.model.StageMode
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

/** Scroll mode (audit 01/11): a long press finds the line under the finger in the scrolled text, not the song's top. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1100dp-h720dp-land-xhdpi", application = android.app.Application::class)
class StageScrollHitTest {
    @get:Rule val compose = createComposeRule()

    private val long = (1..80).joinToString("\n") { "Linha $it da letra de exemplo" }
    private val withChords = (1..80).joinToString("\n") { "[G]Linha $it da [D]letra" }

    private var page by mutableIntStateOf(0)
    private var cursor = -1

    private fun show() = compose.setContent {
        StagebookTheme {
            StageScreen(
                StageUiState(false, listOf(StageSong("Longa", "", long, false, id = "s1")), look = Look(stageMode = StageMode.SCROLL)),
                0, page, { _, p -> page = p }, onEdit = { _, c -> cursor = c },
            )
        }
    }

    private fun editedAt(at: Int): Int {
        page = at
        compose.waitForIdle()
        compose.onRoot().performTouchInput { longClick(center) }
        compose.waitForIdle()
        compose.onNodeWithText("Editar esta linha").performClick()
        compose.waitForIdle()
        return cursor
    }

    @Test fun `after scrolling, editing a line opens the line on screen`() {
        show()
        val top = editedAt(0)
        val scrolled = editedAt(2)
        assertTrue("top=$top scrolled=$scrolled", scrolled > top + 200)
    }

    @Test fun `the chord list at the top scrolls away with the lyrics`() {
        compose.setContent {
            StagebookTheme {
                StageScreen(
                    StageUiState(false, listOf(StageSong("Longa", "", withChords, false, id = "s1")), look = Look(stageMode = StageMode.SCROLL, chordList = ChordListPlace.TOP)),
                    0, page, { _, p -> page = p },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Esconder os acordes desta música").assertIsDisplayed()
        page = 2
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Esconder os acordes desta música").assertIsNotDisplayed()
    }

    // one × in the corner hides the whole list in this song (user, 02/11), instead of one × per chord
    @Test fun `the corner x hides every chord of the song`() {
        var hidden: Set<String>? = null
        compose.setContent {
            StagebookTheme {
                StageScreen(
                    StageUiState(false, listOf(StageSong("Longa", "", withChords, false, id = "s1")), look = Look(chordList = ChordListPlace.TOP)),
                    0, 0, { _, _ -> },
                    onHiddenChords = { _, h -> hidden = h },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Esconder os acordes desta música").performClick()
        compose.waitForIdle()
        org.junit.Assert.assertTrue("escondidos: $hidden", hidden != null && "G" in hidden!!)
    }
}
