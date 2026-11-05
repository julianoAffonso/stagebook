package br.app.stagebook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import br.app.stagebook.ui.setlists.SetlistRowUi
import br.app.stagebook.ui.setlists.SetlistsScreen
import br.app.stagebook.ui.setlists.SetlistsUiState
import br.app.stagebook.ui.setlists.setlistOrder
import br.app.stagebook.ui.songs.SongRowUi
import br.app.stagebook.ui.songs.SongsScreen
import br.app.stagebook.ui.songs.SongsUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Changing the order starts the list at the top (audit 03/11: it followed the old first setlist down the list). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class SetlistOrderScrollTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `switching to A to Z shows the first setlist`() {
        // newest first: "Zebra" on top; in A to Z it goes to the end
        val sets = (listOf("Zebra") + ('A'..'P').map { "Show $it" }).mapIndexed { i, n -> SetlistRowUi("$i", n, 3) }
        var state by mutableStateOf(SetlistsUiState(loading = false, sets = sets))
        compose.setContent { StagebookTheme { SetlistsScreen(state, {}, {}, {}, {}) } }
        compose.onNodeWithText("Zebra").assertIsDisplayed()
        state = SetlistsUiState(loading = false, sets = setlistOrder(sets, abc = true), abc = true)
        compose.waitForIdle()
        compose.onNodeWithText("Show A").assertIsDisplayed()
    }

    @Test fun `a search starts the setlists at the top and the header says how many are left`() {
        val sets = ('A'..'T').map { SetlistRowUi("$it", "Show $it", 3) }
        var state by mutableStateOf(SetlistsUiState(loading = false, sets = sets))
        compose.setContent { StagebookTheme { SetlistsScreen(state, {}, {}, {}, {}) } }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(sets.lastIndex)
        compose.onNodeWithText("Show A").assertDoesNotExist()
        state = SetlistsUiState(loading = false, sets = sets.drop(1) + sets.first(), total = sets.size, query = "show")
        compose.waitForIdle()
        compose.onNodeWithText("Show B").assertIsDisplayed()
        compose.onNodeWithText("20 setlists · 20 no filtro").assertIsDisplayed()
    }

    @Test fun `a filter starts the songs at the top and the header says how many are left`() {
        val songs = ('A'..'T').map { SongRowUi("$it", "Song $it", "") }
        var state by mutableStateOf(SongsUiState(loading = false, songs = songs, total = songs.size, tags = listOf("Rock")))
        compose.setContent { StagebookTheme { SongsScreen(state, "", {}, {}, {}, {}) } }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(songs.lastIndex)
        compose.onNodeWithText("Song A").assertDoesNotExist()
        state = state.copy(songs = songs.dropLast(1), chosenTags = setOf("Rock"))
        compose.waitForIdle()
        compose.onNodeWithText("Song A").assertIsDisplayed()
        compose.onNodeWithText("20 músicas · 19 no filtro").assertIsDisplayed()
    }

    @Test fun `without a filter the header shows only the total`() {
        val songs = listOf(SongRowUi("1", "Song", ""))
        compose.setContent { StagebookTheme { SongsScreen(SongsUiState(loading = false, songs = songs, total = 1), "", {}, {}, {}, {}) } }
        compose.onNodeWithText("no filtro", substring = true).assertDoesNotExist()
    }
}
