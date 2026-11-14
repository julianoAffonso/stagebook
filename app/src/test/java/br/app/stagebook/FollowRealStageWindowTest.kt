package br.app.stagebook

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.BandConduct
import br.app.stagebook.data.BandLink
import br.app.stagebook.data.DeviceIdentity
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.BandProtocol
import br.app.stagebook.domain.Conduct
import br.app.stagebook.navigation.FollowToStage
import br.app.stagebook.navigation.Home
import br.app.stagebook.navigation.Stage
import br.app.stagebook.ui.components.FollowGate
import br.app.stagebook.ui.components.LocalFollowGate
import br.app.stagebook.ui.stage.StageScreen
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Device test v0.1.1134: a follower on the stage with a window open (chord popup, metronome) and the band moving on
 * never got the newest song or break when the window closed, and the strip went away. This runs the follower's real
 * [BandConduct] (the leader's messages come through it), the real stage view model and screen, and [FollowToStage].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi", application = android.app.Application::class)
class FollowRealStageWindowTest {
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val voice = BandProtocol.Hello("voz", "Voz", "key")
    private val gate = FollowGate()
    private lateinit var nav: NavHostController

    private val repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO)).also { r ->
        runBlocking {
            withTimeout(15_000) { r.loaded.first { it } }
            r.restore(emptyList(), emptyList())
            r.updateLook { it.copy(bandOn = true) }
            withTimeout(15_000) { r.library.first { it.look.bandOn } }
        }
    }
    private val ids = listOf("Aaa", "Bbb", "Ccc").map { repo.saveSong(Song(title = it, author = "x", text = "{key: G}\nLetra $it")) }
    private val set = repo.createSetlist("Show").also { repo.addToSetlist(it, ids); repo.addBreak(it) }   // break before Bbb
    private val items = listOf("Aaa", "Bbb", "Ccc").map { Conduct.Item(it, "x") }
    private val followerLink = BandLink(context, DeviceIdentity(context), br.app.stagebook.data.BandTrust(context), br.app.stagebook.data.StageGate(), br.app.stagebook.data.BandInbox(context), repo)
    private val follower = BandConduct(followerLink, repo, DeviceIdentity(context))
    private val leader = BandConduct(BandLink(context, DeviceIdentity(context), br.app.stagebook.data.BandTrust(context), br.app.stagebook.data.StageGate(), br.app.stagebook.data.BandInbox(context), repo), repo, DeviceIdentity(context)).also { c ->
        c.deliver = { msg -> followerLink.conductHandler?.invoke(voice, Conduct.decode(Conduct.encode(msg))!!); emptyMap() }
    }

    private fun show() {
        compose.setContent {
            StagebookTheme {
                CompositionLocalProvider(LocalFollowGate provides gate) {
                    nav = rememberNavController()
                    FollowToStage(nav, follower.openSong, follower.followingSong, gate, remember { androidx.compose.runtime.mutableStateOf(false) })
                    NavHost(nav, startDestination = Stage(songId = ids[0])) {
                        composable<Home> { androidx.compose.material3.Text("home") }
                        composable<Stage> { entry ->
                            val r = entry.toRoute<Stage>()
                            val vm = remember(entry) { testStage(context, repo, SavedStateHandle(mapOf("songId" to r.songId, "leaderBreak" to r.leaderBreak, "start" to 0)), conduct = follower) }
                            // StageRoute without the metronome's native click (the test JVM has no audio library)
                            val ui by vm.uiState.collectAsStateWithLifecycle()
                            if (!ui.loading) StageScreen(ui, 0, 0, { _, _ -> }, onMetronomeSound = {})
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun settle() {
        // the real follower's flows run on IO: give them a moment, then let the screen catch up
        Thread.sleep(300)
        compose.waitForIdle()
        Thread.sleep(100)
        compose.waitForIdle()
    }

    private fun leaderOpens(index: Int) { leader.open(set, index); settle() }
    private fun leaderBreak() { leader.rest(set, 0L); settle() }
    private fun openMetronome() { compose.onNodeWithContentDescription("Metrônomo").performClick(); compose.onNodeWithContentDescription("Mais devagar").assertExists() }
    private fun closeWindow() { compose.onNodeWithContentDescription("Fechar").performClick(); settle() }

    private fun begin() {
        leader.start(set, "Show", items, 0)
        Thread.sleep(300)
        show()
        settle()
        compose.onNodeWithText("Letra Aaa", substring = true).assertExists()
    }

    @Test fun `window open, the band goes to the next song, closing the window opens it`() {
        begin()
        openMetronome()
        leaderOpens(1)
        assertEquals(ids[1], follower.followingSong.value)
        compose.onAllNodesWithText("Letra Bbb", substring = true).assertCountEquals(0)
        assertEquals(ids[1], gate.pending?.target)
        closeWindow()
        compose.onNodeWithText("Letra Bbb", substring = true).assertExists()
        assertNull(gate.pending)
    }

    @Test fun `window open, the band goes to the break, closing the window shows it`() {
        begin()
        openMetronome()
        leaderBreak()
        assertEquals(true, gate.breakHeld)
        closeWindow()
        compose.onNodeWithText("INTERVALO").assertExists()
        assertFalse(gate.breakHeld)
    }

    @Test fun `window open, the band goes to the break and then to the next song`() {
        begin()
        openMetronome()
        leaderBreak()
        leaderOpens(1)
        closeWindow()
        compose.onNodeWithText("Letra Bbb", substring = true).assertExists()
        assertNull(gate.pending)
    }

    @Test fun `window open, the band changes twice`() {
        begin()
        openMetronome()
        leaderOpens(1)
        leaderOpens(2)
        closeWindow()
        compose.onNodeWithText("Letra Ccc", substring = true).assertExists()
    }
}
