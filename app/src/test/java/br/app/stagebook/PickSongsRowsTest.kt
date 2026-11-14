package br.app.stagebook

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.Song
import br.app.stagebook.ui.setlists.PickRowUi
import br.app.stagebook.ui.setlists.PickSongsScreen
import br.app.stagebook.ui.setlists.PickSongsUiState
import br.app.stagebook.ui.setlists.PickSongsViewModel
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Picking songs for a setlist shows each song as the song list does: the artist alone under the name and, at the end
 * of the row, the key, the tempo, the capo and the backing track mark. Checking and adding work as before.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w412dp-h915dp-port-xxhdpi", application = android.app.Application::class)
class PickSongsRowsTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun `the rows get the artist alone and the song's key, tempo, capo and track, and checking and adding still work`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())   // the search waits for the typing to stop on its clock
        try {
            runBlocking {
                val repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
                withTimeout(5_000) { repo.loaded.first { it } }
                val ids = listOf(
                    Song(title = "Primeira", author = " Banda Fictícia ", year = "1985", text = "{key: Em}\n{tempo: 120}\n{capo: 2}\nlinha", track = "a.ogg"),
                    Song(title = "Segunda", author = "Trio Imaginário", year = "1999", text = "{key: G}\nlinha"),
                    Song(title = "Terceira", author = "", year = "2003", text = "linha"),
                ).map(repo::saveSong)
                val set = repo.createSetlist("Show").also { repo.addToSetlist(it, listOf(ids[2])) }
                val vm = PickSongsViewModel(repo, SavedStateHandle(mapOf("setId" to set)))
                val job = CoroutineScope(Dispatchers.Main).launch { vm.uiState.collect {} }
                suspend fun state(ready: (PickSongsUiState) -> Boolean) = withTimeout(10_000) { vm.uiState.first(ready) }

                val rows = state { s -> s.rows.size == 3 && s.rows.any { it.songKey == "Em" } }.rows
                val first = rows.first { it.title == "Primeira" }
                assertEquals("Banda Fictícia", first.subtitle)
                assertEquals(listOf("Em", 120, 2, true), listOf(first.songKey, first.bpm, first.capo, first.hasTrack))
                val second = rows.first { it.title == "Segunda" }
                assertEquals(listOf("Trio Imaginário", "G", 0, 0, false), listOf(second.subtitle, second.songKey, second.bpm, second.capo, second.hasTrack))
                val third = rows.first { it.title == "Terceira" }
                assertEquals(listOf("", "", true, false), listOf(third.subtitle, third.songKey, third.inSet, third.selected))

                // a key written later reaches the row
                repo.updateSong(ids[1]) { it.copy(text = "{key: A}\nlinha") }
                state { s -> s.rows.any { it.title == "Segunda" && it.songKey == "A" } }

                // checked in order, then added in that order after what the setlist had
                assertEquals(true, vm.toggle(ids[1]))
                assertEquals(true, vm.toggle(ids[0]))
                val picked = state { it.selectedCount == 2 }
                assertEquals(listOf("Primeira", "Segunda"), picked.rows.filter { it.selected }.map { it.title }.sorted())
                vm.confirm()
                assertEquals(listOf(ids[2], ids[1], ids[0]), repo.library.value.setlist(set)!!.items)
                job.cancel()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `a row draws the artist without the year, the key, the tempo, the capo and the mark, and a tap checks it`() {
        val toggled = ArrayList<String>()
        var confirmed = 0
        val state = PickSongsUiState(
            setName = "Show",
            rows = listOf(
                PickRowUi("1", "Com tudo", "Banda Fictícia", inSet = false, selected = true, songKey = "Em", bpm = 120, capo = 2, hasTrack = true),
                PickRowUi("2", "Só o tom", "Trio Imaginário", inSet = false, selected = false, songKey = "G"),
                PickRowUi("3", "Já escolhida", "Banda Fictícia", inSet = true, selected = false, songKey = "C"),
            ),
            selectedCount = 1,
        )
        compose.setContent { StagebookTheme { PickSongsScreen(state, "", {}, { toggled += it }, { confirmed++ }, {}) } }
        compose.onNodeWithText("Banda Fictícia", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Trio Imaginário", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Em", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("G", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("120 bpm", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("capo 2", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Backing track", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText("19", substring = true, useUnmergedTree = true).assertCountEquals(0)
        // a song already in the setlist says so, keeps its key and can't be checked
        compose.onNodeWithText("Já está no setlist", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("C", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Já escolhida", useUnmergedTree = true).performClick()
        assertEquals(emptyList<String>(), toggled)
        compose.onNodeWithText("Só o tom", useUnmergedTree = true).performClick()
        assertEquals(listOf("2"), toggled)
        compose.onNodeWithText("Adicionar (1)", useUnmergedTree = true).performClick()
        assertEquals(1, confirmed)
    }
}
