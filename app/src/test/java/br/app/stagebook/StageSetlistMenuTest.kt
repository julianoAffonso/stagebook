package br.app.stagebook

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.app.stagebook.domain.SetlistShow.State
import br.app.stagebook.ui.stage.SetlistActions
import br.app.stagebook.ui.stage.StageScreen
import br.app.stagebook.ui.stage.StageSetlist
import br.app.stagebook.ui.stage.StageSong
import br.app.stagebook.ui.stage.StageUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * V08: the setlist's screen is only the edit now, so what it did for the show lives in the ⋮ of the stage's setlist
 * panel: Lead the band, Share, Edit setlist and Clear played, each with its own name and icon.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi", application = android.app.Application::class)
class StageSetlistMenuTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val songs = listOf("Primeira", "Segunda", "Terceira").mapIndexed { i, t -> StageSong(t, "", "Uma linha", false, id = "s$i") }
    private val log = mutableListOf<String>()
    private val actions = SetlistActions({ log += "conduct" }, { log += "share" }, { log += "edit" }, { log += "clear" })

    private fun show(setlist: StageSetlist) {
        compose.setContent {
            StagebookTheme {
                StageScreen(
                    StageUiState(loading = false, songs = songs, live = true, setlist = setlist), 1, 0, { _, _ -> }, onBack = {},
                    setlistActions = actions,
                )
            }
        }
        compose.onNodeWithContentDescription("Abrir o setlist").performClick()
        compose.waitForIdle()
    }

    private val full = StageSetlist(
        "Show", listOf(State.PLAYED, State.NOW, State.NEXT), listOf("G", "Em", ""), id = "set", anyPlayed = true, canConduct = true,
    )
    private val inPanel = hasAnyAncestor(hasTestTag("stage_setlist"))
    private fun menu() { compose.onNode(hasText("Show") and inPanel).assertIsDisplayed(); compose.onNodeWithContentDescription("Mais opções").performClick(); compose.waitForIdle() }

    @Test fun `the panel has a menu with the four actions, by their names`() {
        show(full)
        menu()
        listOf("Conduzir a banda", "Compartilhar", "Editar setlist", "Limpar tocadas").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test fun `each action does its own thing`() {
        show(full)
        menu(); compose.onNodeWithText("Conduzir a banda").performClick()
        show2("Compartilhar")
        show2("Editar setlist")
        assertEquals(listOf("conduct", "share", "edit"), log)
    }

    // reopens the panel (it closes after an action that leaves the stage) and the menu, and taps the item
    private fun show2(item: String) {
        compose.onNodeWithContentDescription("Abrir o setlist").performClick()
        compose.waitForIdle()
        menu()
        compose.onNodeWithText(item).performClick()
        compose.waitForIdle()
    }

    @Test fun `clear played clears and keeps the panel open`() {
        show(full)
        menu(); compose.onNodeWithText("Limpar tocadas").performClick()
        compose.waitForIdle()
        assertEquals(listOf("clear"), log)
        compose.onNode(hasText("Show") and inPanel).assertIsDisplayed()
    }

    @Test fun `without a band there is no Lead the band, and with nothing played no Clear played`() {
        show(full.copy(canConduct = false, anyPlayed = false))
        menu()
        compose.onNodeWithText("Conduzir a banda").assertDoesNotExist()
        compose.onNodeWithText("Limpar tocadas").assertDoesNotExist()
        compose.onNodeWithText("Compartilhar").assertIsDisplayed()
        compose.onNodeWithText("Editar setlist").assertIsDisplayed()
    }
}
