package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import br.app.stagebook.ui.setlists.SetItemUi
import br.app.stagebook.ui.setlists.SetlistDetailScreen
import br.app.stagebook.ui.setlists.SetlistDetailUiState
import br.app.stagebook.ui.songs.SongRowUi
import br.app.stagebook.ui.songs.SongsScreen
import br.app.stagebook.ui.songs.SongsUiState
import br.app.stagebook.ui.songs.shortName
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Asking to delete ONE song names it (user, 14/11: the window said only "1 song"); several keep the count. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1100dp-h720dp-land-xhdpi", application = android.app.Application::class)
class DeleteAsksNameTest {
    @get:Rule val compose = createComposeRule()

    private val long = "Uma música com um nome absurdamente comprido que não cabe numa pergunta"
    private val state = SongsUiState(
        loading = false,
        songs = listOf(
            SongRowUi("1", "Hazard", "Richard Marx"),
            SongRowUi("2", "Instrumental", ""),
            SongRowUi("3", long, "Banda"),
        ),
        total = 3,
    )

    private fun ask(vararg ids: String) {
        compose.setContent { StagebookTheme { SongsScreen(state, "", {}, {}, {}, {}, initialSelection = ids.toList()) } }
        compose.onNodeWithContentDescription("Apagar").performClick()
    }

    @Test fun `one song - the name and the artist`() {
        ask("1")
        compose.onNodeWithText("Apagar “Hazard”, de Richard Marx? Ela sai também dos setlists.").assertExists()
    }

    @Test fun `one song without artist - only the name`() {
        ask("2")
        compose.onNodeWithText("Apagar “Instrumental”? Ela sai também dos setlists.").assertExists()
    }

    @Test fun `several songs - the count`() {
        ask("1", "2", "3")
        compose.onNodeWithText("Apagar 3 músicas? Elas saem também dos setlists.").assertExists()
    }

    @Test fun `a very long name is cut with an ellipsis`() {
        ask("3")
        compose.onNodeWithText("Apagar “${shortName(long)}”, de Banda? Ela sai também dos setlists.").assertExists()
        assertEquals(40, shortName(long).length)
        assert(shortName(long).endsWith("…"))
    }

    @Test fun `taking one song out of a setlist names it too`() {
        val set = SetlistDetailUiState(
            loading = false, name = "Sábado",
            items = listOf(SetItemUi("a", "1", "Hazard", "Richard Marx", false), SetItemUi("b", "2", "Instrumental", "", false)),
        )
        compose.setContent { StagebookTheme { SetlistDetailScreen(set, null, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, startEditing = true) } }
        compose.onNodeWithText("Hazard").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("Tirar").performClick()
        compose.onNodeWithText("Tirar “Hazard” deste setlist? Ela continua na biblioteca.").assertExists()
    }
}
