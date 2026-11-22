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
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
import br.app.stagebook.ui.components.FollowPending
import br.app.stagebook.ui.components.LocalFollowGate
import br.app.stagebook.navigation.Settings
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
 * The Settings page waits for the leader's song like the editor does (device test v0.1.1123: the stage opened over
 * Settings at once, with no strip and no "Go"; decision of 14/11: tell them there too). The Songs and Setlists tabs go on
 * pulling. HomeScreen needs Hilt, so this drives the real [FollowToStage] with a Home and a Settings page in its place.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-port-xxhdpi", application = android.app.Application::class)
class FollowWaitsInSettingsTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var nav: NavHostController
    private val gate = FollowGate()
    private val songs = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private val following = MutableStateFlow<String?>("a")

    private fun start(openSettings: Boolean = false, alone: Boolean = false) {
        compose.setContent {
            StagebookTheme {
                CompositionLocalProvider(LocalFollowGate provides gate) {
                    nav = rememberNavController()
                    FollowToStage(nav, songs, following, gate)
                    NavHost(nav, startDestination = Home) {
                        composable<Home> {
                            androidx.compose.foundation.layout.Column {
                                Text("home")
                                Text("open settings", Modifier.clickable { nav.navigate(Settings()) })
                                Text("open about", Modifier.clickable { nav.navigate(Settings("ABOUT", alone = true)) })
                            }
                        }
                        composable<Settings> {
                            androidx.compose.foundation.layout.Column {
                                Text("settings")
                                Text("leave settings", Modifier.clickable { nav.popBackStack() })
                            }
                        }
                        composable<Stage> { entry -> Text("stage ${entry.toRoute<Stage>().songId}") }
                    }
                }
            }
        }
        compose.waitForIdle()
        if (openSettings) {
            compose.runOnIdle { nav.navigate(Settings("ABOUT", alone)) }
            compose.waitForIdle()
        }
    }

    private fun band(song: String) {
        following.value = song
        compose.runOnIdle { songs.tryEmit(song) }
        compose.waitForIdle()
    }

    @Test fun `on Settings the band's song waits, the strip says so, and Go opens it at once`() {
        start(openSettings = true)
        band("b")
        compose.onAllNodesWithText("stage b").assertCountEquals(0)
        assertEquals(FollowPending("b", FollowWait.SCREEN), gate.pending)
        compose.runOnIdle { gate.goNow() }
        compose.waitForIdle()
        compose.onNodeWithText("stage b").assertExists()
        assertNull(gate.pending)
    }

    @Test fun `leaving Settings opens the newest song, if the band is still on it`() {
        start(openSettings = true)
        band("b")
        band("c")
        compose.onNodeWithText("leave settings").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("stage c").assertExists()
        compose.onAllNodesWithText("stage b").assertCountEquals(0)
    }

    @Test fun `the song that waited opens in place of Settings, and back from it does not land on Settings`() {
        start(openSettings = true)
        band("b")
        compose.runOnIdle { gate.goNow() }
        compose.waitForIdle()
        compose.runOnIdle { nav.popBackStack() }
        compose.waitForIdle()
        compose.onNodeWithText("home").assertExists()
        compose.onAllNodesWithText("settings").assertCountEquals(0)
    }

    @Test fun `leaving Settings after the band went somewhere this device doesn't have opens nothing`() {
        start(openSettings = true)
        band("b")
        following.value = null
        compose.waitForIdle()
        assertNull(gate.pending)
        compose.onNodeWithText("leave settings").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("stage b").assertCountEquals(0)
    }

    @Test fun `Home pulls to the stage at once, as before`() {
        start()
        band("b")
        compose.onNodeWithText("stage b").assertExists()
        assertNull(gate.pending)
    }

    @Test fun `About, opened alone, waits too`() {
        start(openSettings = true, alone = true)
        band("b")
        compose.onAllNodesWithText("stage b").assertCountEquals(0)
        assertEquals(FollowWait.SCREEN, gate.pending?.reason)
    }
}
