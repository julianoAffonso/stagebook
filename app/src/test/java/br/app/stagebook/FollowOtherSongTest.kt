package br.app.stagebook

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import br.app.stagebook.domain.FollowWait
import br.app.stagebook.navigation.FollowToStage
import br.app.stagebook.navigation.Home
import br.app.stagebook.navigation.Stage
import br.app.stagebook.ui.band.goNow
import br.app.stagebook.ui.components.FollowGate
import br.app.stagebook.ui.components.LocalFollowGate
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Following the band doesn't lock the stage: a stage on the song the band was on moves along with it, but a stage the
 * person took to another song stays there, and the strip says what the band is on, with "Go" (user, 15/11).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-port-xxhdpi", application = android.app.Application::class)
class FollowOtherSongTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var nav: NavHostController
    private val gate = FollowGate()
    private val songs = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private val following = MutableStateFlow<String?>("a")

    private fun start() {
        compose.setContent {
            StagebookTheme {
                CompositionLocalProvider(LocalFollowGate provides gate) {
                    nav = rememberNavController()
                    FollowToStage(nav, songs, following, gate)
                    NavHost(nav, startDestination = Home) {
                        composable<Home> { Text("home") }
                        composable<Stage> { Text("stage ${it.toRoute<Stage>().songId}") }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun stageOn(song: String) { compose.runOnIdle { nav.navigate(Stage(songId = song)) }; compose.waitForIdle() }

    private fun band(song: String) {
        following.value = song
        compose.runOnIdle { songs.tryEmit(song) }
        compose.waitForIdle()
    }

    @Test fun `a stage on the band's song moves along with the band`() {
        start(); stageOn("a")
        band("b")
        compose.onNodeWithText("stage b").assertExists()
        assertNull(gate.pending)
    }

    @Test fun `a stage on another song stays, and waits with Go`() {
        start(); stageOn("z")
        band("b")
        compose.onNodeWithText("stage z").assertExists()
        assertEquals("b", gate.pending?.target)
        assertEquals(FollowWait.OTHER_SONG, gate.pending?.reason)
        compose.runOnIdle { gate.goNow() }
        compose.waitForIdle()
        compose.onNodeWithText("stage b").assertExists()
        assertNull(gate.pending)
    }

    @Test fun `the band moves again while the person is elsewhere - the notice names the newest song`() {
        start(); stageOn("z")
        band("b"); band("c")
        compose.onNodeWithText("stage z").assertExists()
        assertEquals("c", gate.pending?.target)
    }

    @Test fun `a menu closing does not pull a stage that is on another song`() {
        start(); stageOn("z")
        compose.runOnIdle { gate.menus++ }
        compose.waitForIdle()
        band("b")
        assertEquals(FollowWait.MENU, gate.pending?.reason)
        compose.runOnIdle { gate.menus-- }
        compose.waitForIdle()
        compose.onNodeWithText("stage z").assertExists()
        assertEquals(FollowWait.OTHER_SONG, gate.pending?.reason)
    }

    @Test fun `reaching the band's song by yourself clears the notice`() {
        start(); stageOn("z")
        band("b")
        stageOn("b")
        assertNull(gate.pending)
    }

    @Test fun `a stage already on the song the band says does not get a notice`() {
        following.value = null
        start(); stageOn("a")
        band("a")
        compose.onNodeWithText("stage a").assertExists()
        assertNull(gate.pending)
    }
}
