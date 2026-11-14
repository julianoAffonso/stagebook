package br.app.stagebook

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
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
import br.app.stagebook.domain.BandProtocol
import br.app.stagebook.domain.BreakTimer
import br.app.stagebook.domain.Conduct
import br.app.stagebook.domain.SetlistShow.State
import br.app.stagebook.ui.setlists.SetlistDetailViewModel
import br.app.stagebook.ui.stage.StageScreen
import br.app.stagebook.ui.stage.StageSetlist
import br.app.stagebook.ui.stage.StageSong
import br.app.stagebook.ui.stage.StageUiState
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The break's line in the stage's setlist panel says "INTERVALO · mm:ss" (running, or stopped with the pause icon), and the
 * setlist's screen of a device that follows the band says the leader's time, until the leader's break ends (device test
 * v0.1.1123: no time in the panel, and "INTERVALO · 18:43" five minutes into the next song on the follower).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi", application = android.app.Application::class)
class BreakTimeInPanelTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val songs = listOf("Primeira", "Segunda", "Terceira").map { StageSong(it, "", "Uma linha", false, id = it, artist = "x") }
    private val inPanel = hasAnyAncestor(hasTestTag("stage_setlist"))

    private fun show(open: Map<Int, BreakTimer.Timer>) {
        val setlist = StageSetlist("Show", listOf(State.NOW, State.NEXT, State.NONE), listOf("G", "G", "G"), open)
        compose.setContent {
            StagebookTheme { StageScreen(StageUiState(loading = false, songs = songs, live = true, breaks = listOf(2), setlist = setlist), 0, 0, { _, _ -> }, onBack = {}) }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Abrir o setlist").performClick()
        compose.waitForIdle()
    }

    @Test fun `an open break says its time in the panel, stopped with the pause icon`() {
        show(mapOf(2 to BreakTimer.Timer("d", 0L, 750_000L, open = true)))
        compose.onNode(hasText("INTERVALO · 12:30") and inPanel).assertExists()
        compose.onNode(hasText("Pausado") or androidx.compose.ui.test.hasContentDescription("Pausado")).assertExists()
    }

    @Test fun `a running one counts in the panel`() {
        show(mapOf(2 to BreakTimer.Timer("d", System.currentTimeMillis() - 65_000, 0L, open = true)))
        compose.onNode(hasText("INTERVALO · 01:0", substring = true) and inPanel).assertExists()
        compose.onAllNodesWithContentDescription("Pausado").assertCountEquals(0)
    }

    @Test fun `with no open break the panel says just the word`() {
        show(emptyMap())
        compose.onNode(hasText("INTERVALO") and inPanel).assertExists()
        compose.onNode(hasText("INTERVALO ·", substring = true) and inPanel).assertDoesNotExist()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class FollowerBreakShownTest {
    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun reset() = Dispatchers.resetMain()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val voice = BandProtocol.Hello("voz", "Voz", "key")
    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private inner class Tablet {
        val repo = LibraryRepository(context, scope()).also { r ->
            runBlocking {
                withTimeout(5_000) { r.loaded.first { it } }
                r.restore(emptyList(), emptyList())
                r.updateLook { it.copy(bandOn = true) }
                withTimeout(5_000) { r.library.first { it.look.bandOn } }
            }
        }
        val ids = listOf("A", "B", "C", "D", "E").map { repo.saveSong(Song(title = it, author = "x", text = "{key: G}\n$it")) }
        val set = repo.createSetlist("Show").also { repo.addToSetlist(it, ids); repo.addBreak(it); repo.moveRow(it, 4, 2) }   // A B | C D E
        val show = SetlistShowStore(context, repo, scope()).also { it.today = { "2026-11-14" } }
        val items = listOf("A", "B", "C", "D", "E").map { Conduct.Item(it, "x") }
        private val identity = DeviceIdentity(context)
        val link = BandLink(context, identity, BandTrust(context), StageGate(), BandInbox(context), repo)
        val conduct = BandConduct(link, repo, identity)
        private val cloud = SetlistCloud(repo, identity)
        private val connectivity = Connectivity(context)
        val vm = SetlistDetailViewModel(
            repo, SavedStateHandle(mapOf("setId" to set)), cloud, connectivity, link, BandTrust(context),
            BandInternet(context, cloud, BandTrust(context), identity, BandInbox(context), connectivity, link), conduct, show,
        )

        fun leader(index: Int, breakMs: Long = -1, paused: Boolean = false) {
            link.conductHandler!!.invoke(voice, Conduct.Msg(Conduct.OPEN, "Voz", "Show", items, index, breakMs = breakMs, breakPaused = paused))
        }

        fun open() = runBlocking { withTimeout(5_000) { vm.uiState.first { !it.loading && it.items.size == 5 } }.openBreaks }
        fun await(what: (Map<Int, BreakTimer.Timer>) -> Boolean) = runBlocking { withTimeout(5_000) { vm.uiState.first { !it.loading && what(it.openBreaks) } }.openBreaks }
    }

    @Test fun `the follower's setlist shows the leader's break time, running`() {
        val t = Tablet()
        t.leader(1, breakMs = 5_000)
        val timer = t.await { it.isNotEmpty() }.getValue(2)
        assertTrue(timer.running)
        assertTrue(timer.elapsed(System.currentTimeMillis()) in 5_000..15_000)
    }

    @Test fun `and stopped when the leader paused it`() {
        val t = Tablet()
        t.leader(1, breakMs = 9_000, paused = true)
        val timer = t.await { it.isNotEmpty() }.getValue(2)
        assertFalse(timer.running)
        assertEquals(9_000L, timer.elapsed(System.currentTimeMillis() + 600_000))
    }

    @Test fun `a break this tablet had left open itself doesn't show while it follows, and is closed`() {
        val t = Tablet()
        val next = t.repo.library.value.setlist(t.set)!!.items[2]
        t.show.breakEntered(t.set, next)   // opened on its own stage some time ago, never ended
        assertTrue(t.open().isNotEmpty())   // on its own, the setlist shows it
        t.leader(3)   // the band is on song D: no break
        assertTrue(t.await { it.isEmpty() }.isEmpty())
        assertFalse(t.show.breaks.value.getValue(BreakTimer.key(t.set, next)).open)
    }

    @Test fun `the leader going on to the next song ends the break on the follower's setlist`() {
        val t = Tablet()
        t.leader(1, breakMs = 1_000)
        t.await { it.isNotEmpty() }
        t.leader(2)
        assertTrue(t.await { it.isEmpty() }.isEmpty())
    }

    @Test fun `the break is found in the follower's own copy of the setlist, and in no other place`() {
        val timer = BreakTimer.Timer(accumulated = 1L)
        val msg = Conduct.Msg(Conduct.OPEN, songs = listOf("A", "B", "C", "D").map { Conduct.Item(it, "x") }, index = 1, breakMs = 0)
        val mine = listOf("A", "B", "C", "D", "E").map { it to "x" }
        assertEquals(mapOf(2 to timer), BreakTimer.followedAt(mine, listOf(2), msg, timer))
        assertEquals(emptyMap<Int, BreakTimer.Timer>(), BreakTimer.followedAt(mine, listOf(4), msg, timer))
        // the song before it isn't here, but the one after it is
        assertEquals(mapOf(2 to timer), BreakTimer.followedAt(listOf("A" to "x", null, "C" to "x"), listOf(2), msg, timer))
        assertNotNull(msg)
    }
}
