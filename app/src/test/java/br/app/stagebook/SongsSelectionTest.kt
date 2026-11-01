package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.app.stagebook.ui.songs.AddResult
import br.app.stagebook.ui.songs.SetPick
import br.app.stagebook.ui.songs.SongRowUi
import br.app.stagebook.ui.songs.SongsScreen
import br.app.stagebook.ui.songs.SongsUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What's done to several songs lives in the selection (user, 01/11): tags, setlist, share and delete. The row has no
 * buttons; tapping opens the stage (editing is on its pencil).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1100dp-h720dp-land-xhdpi", application = android.app.Application::class)
class SongsSelectionTest {
    @get:Rule val compose = createComposeRule()

    private val state = SongsUiState(
        loading = false,
        songs = listOf(SongRowUi("1", "Hazard", "Richard Marx"), SongRowUi("2", "Tempo Perdido", "Legião Urbana")),
        total = 2,
        sets = listOf(SetPick("s1", "Sábado")),
    )

    @Test fun `deleting the selection asks first, then deletes them all`() {
        var deleted = emptyList<String>()
        compose.setContent { StagebookTheme { SongsScreen(state, "", {}, {}, {}, {}, initialSelection = listOf("1", "2"), onDeleteMany = { deleted = it }) } }
        compose.onNodeWithContentDescription("Apagar").performClick()
        compose.onNodeWithText("Apagar 2 músicas? Elas saem também dos setlists.").assertExists()
        compose.onNodeWithText("Apagar").performClick()   // the dialog's button
        assertEquals(listOf("1", "2"), deleted)
    }

    @Test fun `adding the selection to a setlist`() {
        var added: Pair<List<String>, String?>? = null
        compose.setContent {
            StagebookTheme {
                SongsScreen(state, "", {}, {}, {}, {}, initialSelection = listOf("2"), onAddToSetlist = { ids, set, _ -> added = ids to set; AddResult.Added(ids.size, "Sábado") })
            }
        }
        compose.onNodeWithContentDescription("Adicionar ao setlist").performClick()
        compose.onNodeWithText("Sábado").performClick()
        assertEquals(listOf("2") to "s1", added)
    }


    @Test fun `selecting, the top bar shows how many and select all takes every song`() {
        compose.setContent { StagebookTheme { SongsScreen(state, "", {}, {}, {}, {}, initialSelection = listOf("1")) } }
        compose.onNodeWithText("1").assertExists()
        compose.onNodeWithContentDescription("Selecionar todas").performClick()
        compose.onNodeWithText("2").assertExists()
    }
}
