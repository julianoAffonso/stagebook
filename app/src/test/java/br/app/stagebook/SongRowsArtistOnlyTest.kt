package br.app.stagebook

import androidx.lifecycle.SavedStateHandle
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.BandConduct
import br.app.stagebook.data.BandInbox
import br.app.stagebook.data.BandInternet
import br.app.stagebook.data.BandLink
import br.app.stagebook.data.BandTrust
import br.app.stagebook.data.Connectivity
import br.app.stagebook.data.DeviceIdentity
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.SetlistCloud
import br.app.stagebook.data.SetlistShowStore
import br.app.stagebook.data.StageGate
import br.app.stagebook.data.model.Song
import br.app.stagebook.ui.setlists.SetlistDetailScreen
import br.app.stagebook.ui.setlists.SetlistDetailViewModel
import br.app.stagebook.ui.songs.SongsScreen
import br.app.stagebook.ui.songs.SongsUiState
import br.app.stagebook.ui.songs.SongsViewModel
import br.app.stagebook.ui.stage.StageScreen
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
 * The year left the lists (user, 13/11): a row of the song list, of a setlist and of the setlist over the stage shows
 * the artist alone. The stage's header keeps artist and year. Each screen is drawn from what its view model builds
 * from the same library.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w412dp-h915dp-port-xxhdpi", application = android.app.Application::class)
class SongRowsArtistOnlyTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val main = UnconfinedTestDispatcher()   // the search waits for the typing to stop on its clock

    private class Parts(val repo: LibraryRepository, val ids: List<String>, val set: String, val songs: SongsViewModel, val detail: SetlistDetailViewModel)

    /** A library of three songs, the first with everything a row shows, all in the setlist "Show". */
    private fun library(extra: List<Song> = emptyList()): Parts {
        val repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        runBlocking { withTimeout(5_000) { repo.loaded.first { it } } }
        val ids = listOf(
            Song(title = "Primeira", author = " Banda Fictícia ", year = "1985", text = "{key: Em}\n{tempo: 120}\n{capo: 2}\nlinha", track = "a.ogg"),
            Song(title = "Segunda", author = "Trio Imaginário", year = "1999", text = "{key: G}\nlinha"),
            Song(title = "Terceira", author = "", year = "2003", text = "linha"),
        ).map(repo::saveSong)
        extra.forEach(repo::saveSong)
        val set = repo.createSetlist("Show").also { repo.addToSetlist(it, ids) }
        val identity = DeviceIdentity(context)
        val trust = BandTrust(context)
        val inbox = BandInbox(context)
        val link = BandLink(context, identity, trust, StageGate(), inbox, repo)
        val cloud = SetlistCloud(repo, identity)
        val connectivity = Connectivity(context)
        val internet = BandInternet(context, cloud, trust, identity, inbox, connectivity, link)
        val show = SetlistShowStore(context, repo, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        return Parts(
            repo, ids, set,
            SongsViewModel(repo, connectivity, cloud, link, trust, internet),
            SetlistDetailViewModel(repo, SavedStateHandle(mapOf("setId" to set)), cloud, connectivity, link, trust, internet, BandConduct(link, repo, identity), show),
        )
    }

    /** The view models run on the main dispatcher: an unconfined one while their state is read, the real one to draw. */
    private fun <T> built(read: suspend (Parts) -> T, extra: List<Song> = emptyList()): Pair<Parts, T> {
        Dispatchers.setMain(main)
        try {
            val parts = library(extra)
            return parts to runBlocking { withTimeout(10_000) { read(parts) } }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun noYear() = listOf("1985", "1999", "2003").forEach { compose.onAllNodesWithText(it, substring = true, useUnmergedTree = true).assertCountEquals(0) }

    private suspend fun songsState(parts: Parts, ready: (SongsUiState) -> Boolean): SongsUiState {
        val job = CoroutineScope(Dispatchers.Main).launch { parts.songs.uiState.collect {} }
        return parts.songs.uiState.first { !it.loading && ready(it) }.also { job.cancel() }
    }

    @Test fun `the song list gets the artist alone and the song's key, tempo, capo and track, and draws them`() {
        val (_, state) = built({ p -> songsState(p) { it.songs.size == 3 && it.songs[0].songKey == "Em" } })
        val first = state.songs.first { it.title == "Primeira" }
        assertEquals("Banda Fictícia", first.subtitle)
        assertEquals(listOf("Em", 120, 2, true), listOf(first.songKey, first.bpm, first.capo, first.hasTrack))
        val third = state.songs.first { it.title == "Terceira" }
        assertEquals(listOf("", "", 0, 0, false), listOf(third.subtitle, third.songKey, third.bpm, third.capo, third.hasTrack))
        assertEquals("three songs are a plain list", emptyList<Any>(), state.groups)

        compose.setContent { StagebookTheme { SongsScreen(state, "", {}, {}, {}, {}) } }
        compose.onNodeWithText("Banda Fictícia", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Trio Imaginário", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Em").assertIsDisplayed()
        compose.onNodeWithText("G").assertIsDisplayed()
        compose.onNodeWithText("120 bpm").assertIsDisplayed()
        compose.onNodeWithText("capo 2").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Backing track").assertCountEquals(1)
        noYear()
    }

    @Test fun `the song list follows a key written later, and its letters follow the search and the tags`() {
        val many = ('A'..'J').flatMap { l -> (1..3).map { Song(title = "$l canção $it", author = "Banda", text = "linha", tags = if (it == 1) listOf("Lenta") else emptyList()) } }
        val (states, _) = built({ p ->
            val job = CoroutineScope(Dispatchers.Main).launch { p.songs.uiState.collect {} }
            val all = p.songs.uiState.first { !it.loading && it.songs.size == 33 }
            // the key is read from the lyrics, which the search doesn't look at: the row has to follow it all the same
            p.repo.updateSong(p.ids[2]) { it.copy(text = "{key: D}\nlinha") }
            val keyed = p.songs.uiState.first { s -> s.songs.any { it.title == "Terceira" && it.songKey == "D" } }
            p.songs.onQueryChange("canção 2")
            main.scheduler.advanceUntilIdle()
            val searched = p.songs.uiState.first { it.query == "canção 2" }
            p.songs.onQueryChange("")
            p.songs.onTagsChange(setOf("Lenta"))
            val tagged = p.songs.uiState.first { it.chosenTags.isNotEmpty() }
            p.songs.onTagsChange(emptySet())
            p.songs.onQueryChange("A canção")
            main.scheduler.advanceUntilIdle()
            val few = p.songs.uiState.first { it.query == "A canção" }
            job.cancel()
            listOf(all, keyed, searched, tagged, few)
        }, many).let { it.second to it.first }
        val (all, keyed, searched, tagged, few) = states
        assertEquals(('A'..'J').map { it.toString() } + listOf("P", "S", "T"), all.groups.map { it.letter })
        assertEquals("each letter's first song", all.groups.map { it.letter }, all.groups.map { br.app.stagebook.domain.SongGroups.letter(all.songs[it.first].title) })
        assertEquals("D", keyed.songs.first { it.title == "Terceira" }.songKey)
        // ten songs left by the search, ten by the tag: too few for letters
        assertEquals(10, searched.songs.size)
        assertEquals(emptyList<Any>(), searched.groups)
        assertEquals(10, tagged.songs.size)
        assertEquals(emptyList<Any>(), tagged.groups)
        assertEquals(3, few.songs.size)
        assertEquals(emptyList<Any>(), few.groups)
    }

    @Test fun `a setlist's rows draw the artist alone, in the show and in the edit`() {
        val (_, state) = built({ p -> p.detail.uiState.first { !it.loading && it.items.size == 3 } })
        assertEquals(listOf("Banda Fictícia", "Trio Imaginário", ""), state.items.map { it.subtitle })
        compose.setContent { StagebookTheme { SetlistDetailScreen(state, null, {}, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}) } }
        compose.onNodeWithText("Banda Fictícia", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Trio Imaginário", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("120 bpm").assertIsDisplayed()
        noYear()
        compose.onNodeWithContentDescription("Editar setlist").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Banda Fictícia", useUnmergedTree = true).assertIsDisplayed()
        noYear()
    }

    @Test fun `on stage the header keeps artist and year, and the setlist's panel draws the artist alone`() {
        val (_, state) = built({ p ->
            testStage(context, p.repo, SavedStateHandle(mapOf("setId" to p.set, "start" to 0))).uiState.first { !it.loading && it.songs.size == 3 }
        })
        assertEquals(listOf("Banda Fictícia · 1985", "Trio Imaginário · 1999", "2003"), state.songs.map { it.meta })
        assertEquals(listOf("Banda Fictícia", "Trio Imaginário", ""), state.songs.map { it.artist })
        compose.setContent { StagebookTheme { StageScreen(state, 0, 0, { _, _ -> }, onBack = {}) } }
        compose.onNodeWithText("Banda Fictícia · 1985").assertIsDisplayed()
        compose.onNodeWithContentDescription("Abrir o setlist").performClick()
        compose.waitForIdle()
        val inPanel = hasAnyAncestor(hasTestTag("stage_setlist"))
        compose.onNode(hasText("Banda Fictícia") and inPanel, useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasText("Trio Imaginário") and inPanel, useUnmergedTree = true).assertIsDisplayed()
        listOf("1985", "1999", "2003").forEach { compose.onAllNodes(hasText(it, substring = true) and inPanel, useUnmergedTree = true).assertCountEquals(0) }
        // the header under the panel still says the year
        compose.onNodeWithText("Banda Fictícia · 1985").assertExists()
    }
}
