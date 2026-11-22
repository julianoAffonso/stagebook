package br.app.stagebook

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * Picking songs for a setlist shows each song as the song list does: the artist alone under the name (a song already in the setlist keeps it, tagged) and, at the end
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
                // the numbers go on from the songs the setlist already has (one): the first chosen is the 2nd, the next the 3rd
                assertEquals(1, picked.inSetCount)
                assertEquals(2, picked.rows.first { it.title == "Segunda" }.order)
                assertEquals(3, picked.rows.first { it.title == "Primeira" }.order)
                assertEquals(0, picked.rows.first { it.title == "Terceira" }.order)
                vm.confirm()
                assertEquals(listOf(ids[2], ids[1], ids[0]), repo.library.value.setlist(set)!!.items)
                job.cancel()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `leaving with songs checked and not confirmed adds them in the order checked, once`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            runBlocking {
                val repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
                withTimeout(5_000) { repo.loaded.first { it } }
                val ids = listOf(Song(title = "A", text = "linha"), Song(title = "B", text = "linha"), Song(title = "C", text = "linha")).map(repo::saveSong)
                val set = repo.createSetlist("Show").also { repo.addToSetlist(it, listOf(ids[0])) }
                val vm = PickSongsViewModel(repo, SavedStateHandle(mapOf("setId" to set)))
                val job = CoroutineScope(Dispatchers.Main).launch { vm.uiState.collect {} }
                assertEquals(true, vm.toggle(ids[2]))
                assertEquals(true, vm.toggle(ids[1]))
                vm.confirmPending()   // what the screen does on the way out
                assertEquals(listOf(ids[0], ids[2], ids[1]), repo.library.value.setlist(set)!!.items)
                vm.confirmPending()   // the checks are gone: nothing more to add
                assertEquals(listOf(ids[0], ids[2], ids[1]), repo.library.value.setlist(set)!!.items)
                assertEquals(0, withTimeout(5_000) { vm.uiState.first { it.selectedCount == 0 } }.selectedCount)
                job.cancel()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `the header names the setlist as the way back, the screen's task as the title, and the check counts what the setlist will have`() {
        val state = PickSongsUiState(
            setName = "Show de sábado com um nome bem comprido que não cabe",
            rows = listOf(PickRowUi("1", "Com tudo", "Banda", inSet = false, selected = true, order = 6)),
            selectedCount = 1, inSetCount = 5,
        )
        compose.setContent { StagebookTheme { PickSongsScreen(state, "", {}, {}, {}, {}) } }
        compose.onAllNodesWithText("‹ Show de sábado com um nome bem comprido que não cabe").assertCountEquals(1)
        compose.onAllNodesWithText("Adicionar músicas").assertCountEquals(1)
        // 5 it has + 1 checked = 6: on the check, and the row's own number is the 6th place too
        compose.onAllNodesWithText("6", useUnmergedTree = true).assertCountEquals(2)
    }

    private fun pickerWithOneChecked(): Triple<PickSongsViewModel, LibraryRepository, Pair<String, String>> = runBlocking {
        val repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
        val ids = listOf(Song(title = "A", text = "linha"), Song(title = "B", text = "linha")).map(repo::saveSong)
        val set = repo.createSetlist("Show").also { repo.addToSetlist(it, listOf(ids[0])) }
        val vm = PickSongsViewModel(repo, SavedStateHandle(mapOf("setId" to set)))
        assertEquals(true, vm.toggle(ids[1]))
        Triple(vm, repo, set to ids[1])
    }

    @Test fun `closing the menu with songs checked asks first, and Add puts them in and closes`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val (vm, repo, ids) = pickerWithOneChecked()
            val guard = br.app.stagebook.ui.drawer.DrawerGuard()
            var closed = 0
            compose.setContent {
                androidx.compose.runtime.CompositionLocalProvider(
                    br.app.stagebook.ui.drawer.LocalDrawerGuard provides guard,
                    br.app.stagebook.ui.drawer.LocalDrawer provides br.app.stagebook.ui.drawer.DrawerControl({}, { closed++ }),
                ) { StagebookTheme { br.app.stagebook.ui.setlists.PickSongsRoute({}, vm) } }
            }
            // the checked song comes through the view model's flow: the screen knows it once the count arrives
            compose.waitUntil(5_000) { vm.uiState.value.selectedCount == 1 }
            compose.waitForIdle()
            var asked: Boolean? = null
            compose.runOnUiThread { asked = guard.asks?.invoke() }
            assertEquals("the menu asks the screen before it closes", true, asked)
            compose.waitForIdle()
            compose.onNodeWithText("Adicionar as 1 músicas marcadas", substring = true).assertIsDisplayed()
            assertEquals("nothing went in yet", 1, repo.library.value.setlist(ids.first)!!.items.size)
            compose.onNodeWithText("Adicionar").performClick()
            compose.waitForIdle()
            assertEquals(2, repo.library.value.setlist(ids.first)!!.items.size)
            assertEquals(1, closed)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `closing the menu with songs checked and choosing Discard adds nothing`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val (vm, repo, ids) = pickerWithOneChecked()
            val guard = br.app.stagebook.ui.drawer.DrawerGuard()
            var closed = 0
            compose.setContent {
                androidx.compose.runtime.CompositionLocalProvider(
                    br.app.stagebook.ui.drawer.LocalDrawerGuard provides guard,
                    br.app.stagebook.ui.drawer.LocalDrawer provides br.app.stagebook.ui.drawer.DrawerControl({}, { closed++ }),
                ) { StagebookTheme { br.app.stagebook.ui.setlists.PickSongsRoute({}, vm) } }
            }
            // the checked song comes through the view model's flow: the screen knows it once the count arrives
            compose.waitUntil(5_000) { vm.uiState.value.selectedCount == 1 }
            compose.waitForIdle()
            var asked: Boolean? = null
            compose.runOnUiThread { asked = guard.asks?.invoke() }
            assertEquals("the menu asks the screen before it closes", true, asked)
            compose.waitForIdle()
            compose.onNodeWithText("Descartar").performClick()
            compose.waitForIdle()
            assertEquals(1, repo.library.value.setlist(ids.first)!!.items.size)
            assertEquals(1, closed)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `with nothing checked the menu closes without a question`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val repo = runBlocking { LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO)).also { withTimeout(5_000) { it.loaded.first { l -> l } } } }
            val set = repo.createSetlist("Show")
            val vm = PickSongsViewModel(repo, SavedStateHandle(mapOf("setId" to set)))
            val guard = br.app.stagebook.ui.drawer.DrawerGuard()
            compose.setContent { androidx.compose.runtime.CompositionLocalProvider(br.app.stagebook.ui.drawer.LocalDrawerGuard provides guard) { StagebookTheme { br.app.stagebook.ui.setlists.PickSongsRoute({}, vm) } } }
            compose.waitForIdle()
            assertEquals(false, guard.asks?.invoke())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `the screen being rebuilt does not add what is checked, the checks wait`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val (vm, repo, ids) = pickerWithOneChecked()
            val show = androidx.compose.runtime.mutableStateOf(true)
            compose.setContent { StagebookTheme { if (show.value) br.app.stagebook.ui.setlists.PickSongsRoute({}, vm) } }
            compose.waitForIdle()
            show.value = false   // what pinning the menu or crossing 600 dp does to it: gone from the composition, back a moment later
            compose.waitForIdle()
            show.value = true
            compose.waitForIdle()
            assertEquals("not added by the rebuild", 1, repo.library.value.setlist(ids.first)!!.items.size)
            vm.confirmPending()
            assertEquals(2, repo.library.value.setlist(ids.first)!!.items.size)
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
                PickRowUi("3", "Já escolhida", "Cássia Eller", inSet = true, selected = false, songKey = "C", bpm = 96, capo = 1, hasTrack = true),
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
        compose.onAllNodesWithContentDescription("Backing track", useUnmergedTree = true).assertCountEquals(2)   // the in-setlist row has one too
        compose.onAllNodesWithText("19", substring = true, useUnmergedTree = true).assertCountEquals(0)
        // a song already in the setlist keeps its artist, key, tempo, capo and mark and can't be checked; no phrase (user, 15/11)
        compose.onAllNodesWithText("No setlist", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("Já está no setlist", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithText("Cássia Eller", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("C", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("96 bpm", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("capo 1", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Backing track", useUnmergedTree = true).assertCountEquals(2)
        compose.onNodeWithText("Já escolhida", useUnmergedTree = true).performClick()
        assertEquals(emptyList<String>(), toggled)
        compose.onNodeWithText("Só o tom", useUnmergedTree = true).performClick()
        assertEquals(listOf("2"), toggled)
        compose.onNodeWithContentDescription("Adicionar (1)", useUnmergedTree = true).performClick()
        assertEquals(1, confirmed)
    }
}
