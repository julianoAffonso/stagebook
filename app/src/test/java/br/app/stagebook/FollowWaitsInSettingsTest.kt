package br.app.stagebook

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import br.app.stagebook.ui.components.FollowHoldWhile
import br.app.stagebook.ui.components.FollowPending
import br.app.stagebook.ui.components.LocalFollowGate
import br.app.stagebook.ui.home.HomeTab
import br.app.stagebook.ui.home.settingsFront
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings (a tab of Home, and the screens inside it) waits for the leader's song like the editor does (device test
 * v0.1.1123: the stage opened over Settings at once, with no strip and no "Go"; decision of 14/11: tell them there too).
 * The Songs and Setlists tabs go on pulling. HomeScreen needs Hilt, so this drives the real [FollowToStage] with a Home
 * that says what HomeScreen says ([FollowHoldWhile] with [settingsFront]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-port-xxhdpi", application = android.app.Application::class)
class FollowWaitsInSettingsTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var nav: NavHostController
    private val gate = FollowGate()
    private val songs = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private val following = MutableStateFlow<String?>("a")

    private fun start(first: HomeTab) {
        compose.setContent {
            StagebookTheme {
                val hold = remember { mutableStateOf(false) }
                CompositionLocalProvider(LocalFollowGate provides gate, br.app.stagebook.ui.components.LocalFollowHold provides hold) {
                    nav = rememberNavController()
                    FollowToStage(nav, songs, following, gate, hold)
                    NavHost(nav, startDestination = Home) {
                        composable<Home> {
                            var tab by remember { mutableStateOf(first) }
                            var about by remember { mutableStateOf(false) }
                            FollowHoldWhile(settingsFront(tab, about))
                            androidx.compose.foundation.layout.Column {
                                Text("tab ${tab.name} about=$about")
                                Text("open about", Modifier.clickable { about = true })
                                Text("go songs", Modifier.clickable { tab = HomeTab.SONGS })
                                Text("go setlists", Modifier.clickable { tab = HomeTab.SETLISTS })
                            }
                        }
                        composable<Stage> { entry -> Text("stage ${entry.toRoute<Stage>().songId}") }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun band(song: String) {
        following.value = song
        compose.runOnIdle { songs.tryEmit(song) }
        compose.waitForIdle()
    }

    @Test fun `on Settings the band's song waits, the strip says so, and Go opens it at once`() {
        start(HomeTab.SETTINGS)
        band("b")
        compose.onAllNodesWithText("stage b").assertCountEquals(0)
        assertEquals(FollowPending("b", FollowWait.SCREEN), gate.pending)
        compose.runOnIdle { gate.goNow() }
        compose.waitForIdle()
        compose.onNodeWithText("stage b").assertExists()
        assertNull(gate.pending)
    }

    @Test fun `leaving Settings for another tab opens the newest song, if the band is still on it`() {
        start(HomeTab.SETTINGS)
        band("b")
        band("c")
        compose.onNodeWithText("go setlists").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("stage c").assertExists()
        compose.onAllNodesWithText("stage b").assertCountEquals(0)
    }

    @Test fun `leaving Settings after the band went somewhere this device doesn't have opens nothing`() {
        start(HomeTab.SETTINGS)
        band("b")
        following.value = null
        compose.waitForIdle()
        assertNull(gate.pending)
        compose.onNodeWithText("go songs").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("stage b").assertCountEquals(0)
    }

    @Test fun `the Songs and Setlists tabs pull to the stage at once, as before`() {
        start(HomeTab.SONGS)
        band("b")
        compose.onNodeWithText("stage b").assertExists()
        assertNull(gate.pending)
    }

    @Test fun `About, opened from the rail, waits too`() {
        start(HomeTab.SETLISTS)
        compose.onNodeWithText("open about").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("tab SETLISTS about=true").assertExists()
        band("b")
        compose.onAllNodesWithText("stage b").assertCountEquals(0)
        assertEquals(FollowWait.SCREEN, gate.pending?.reason)
    }

    @Test fun `Settings is in front on its tab or on About, and nowhere else`() {
        assertTrue(settingsFront(HomeTab.SETTINGS, false))
        assertTrue(settingsFront(HomeTab.SONGS, true))
        assertFalse(settingsFront(HomeTab.SONGS, false))
        assertFalse(settingsFront(HomeTab.SETLISTS, false))
    }
}
