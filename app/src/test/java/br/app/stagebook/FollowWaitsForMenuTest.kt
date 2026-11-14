package br.app.stagebook

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import br.app.stagebook.domain.FollowWait
import br.app.stagebook.domain.PendingFollow
import br.app.stagebook.domain.followBlock
import br.app.stagebook.navigation.FollowToStage
import br.app.stagebook.navigation.Home
import br.app.stagebook.navigation.SongEditor
import br.app.stagebook.navigation.Stage
import br.app.stagebook.ui.band.ConductBar
import br.app.stagebook.ui.band.ConductUi
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.components.ConfirmDialog
import br.app.stagebook.ui.components.FollowGate
import br.app.stagebook.ui.components.FollowPending
import br.app.stagebook.ui.components.LocalFollowGate
import br.app.stagebook.ui.components.MenuAction
import br.app.stagebook.ui.components.OverflowMenu
import br.app.stagebook.ui.theme.Sizes
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A follower in the middle of an action (a menu or a window open) isn't taken to the stage when the band changes songs:
 * the song waits, and opens when the menu closes (before, the stage opened over the menu, which vanished mid-tap).
 * StagebookNavHost needs Hilt, so this drives FollowToStage, the part it is made of, with the app's real menus.
 *
 * What this does NOT cover: the window losing focus when a menu or window opens (Robolectric never moves focus to a
 * popup window: checked, it stays focused), and the real keyboard insets. Those two are only a hypothesis here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-port-xxhdpi", application = android.app.Application::class)
class FollowWaitsForMenuTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var nav: NavHostController
    private val gate = FollowGate()
    private val songs = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private val following = MutableStateFlow<String?>("b")

    private fun start(home: @androidx.compose.runtime.Composable () -> Unit = { Text("home") }) {
        compose.setContent {
            StagebookTheme {
                CompositionLocalProvider(LocalFollowGate provides gate) {
                    nav = rememberNavController()
                    FollowToStage(nav, songs, following, gate, androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) })
                    NavHost(nav, startDestination = Home) {
                        composable<Home> { home() }
                        composable<SongEditor> { Text("editor") }
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

    private fun menuHome() = start { OverflowMenu(listOf(MenuAction("Editar", AppIcons.Edit, {}))) }

    @Test fun `with the menu open the song waits, then opens by itself when the menu closes`() {
        menuHome()
        compose.onNodeWithContentDescription("Mais opções").performClick()
        compose.onNodeWithText("Editar").assertExists()
        band("b")
        compose.onNodeWithText("Editar").assertExists()
        assertEquals(FollowPending("b", FollowWait.MENU), gate.pending)
        // closing it with a choice
        compose.onNodeWithText("Editar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("stage b").assertExists()
        assertNull(gate.pending)
    }

    @Test fun `with nothing open the song opens at once`() {
        menuHome()
        band("b")
        compose.onNodeWithText("stage b").assertExists()
        assertNull(gate.pending)
    }

    @Test fun `a window open holds it too`() {
        start {
            var show by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
            if (show) ConfirmDialog("Apagar tudo?", "Apagar", {}, { show = false })
        }
        band("b")
        compose.onNodeWithText("Apagar tudo?").assertExists()
        assertEquals(FollowWait.MENU, gate.pending?.reason)
        compose.onNodeWithText("Apagar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("stage b").assertExists()
    }

    @Test fun `only the newest song opens, and a band that moved on and away leaves nothing waiting`() {
        menuHome()
        compose.onNodeWithContentDescription("Mais opções").performClick()
        band("a")
        band("b")
        assertEquals("b", gate.pending?.target)
        // the band goes to a song this device doesn't have: nothing to open
        following.value = null
        compose.waitForIdle()
        assertNull(gate.pending)
        compose.onNodeWithText("Editar").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("stage a").assertCountEquals(0)
        compose.onAllNodesWithText("stage b").assertCountEquals(0)
    }

    @Test fun `Go opens the stage at once, with the menu still open`() {
        menuHome()
        compose.onNodeWithContentDescription("Mais opções").performClick()
        band("b")
        compose.runOnIdle { gate.go() }
        compose.waitForIdle()
        compose.onNodeWithText("stage b").assertExists()
        assertNull(gate.pending)
    }

    @Test fun `on a screen that was already waiting the strip says it too, and back from the stage returns to it`() {
        start()
        compose.runOnIdle { nav.navigate(SongEditor()) }
        compose.waitForIdle()
        band("b")
        compose.onNodeWithText("editor").assertExists()
        assertEquals(FollowWait.SCREEN, gate.pending?.reason)
        compose.runOnIdle { gate.go() }
        compose.waitForIdle()
        compose.onNodeWithText("stage b").assertExists()
        compose.runOnIdle { nav.popBackStack() }
        compose.waitForIdle()
        compose.onNodeWithText("editor").assertExists()
    }

    @Test fun `the reasons by priority`() {
        assertNull(followBlock(true, false, false, false, false))
        assertEquals(FollowWait.SCREEN, followBlock(false, false, false, true, true))
        assertEquals(FollowWait.SCREEN, followBlock(true, true, false, false, false))
        assertEquals(FollowWait.MENU, followBlock(true, false, false, true, true))
        assertEquals(FollowWait.KEYBOARD, followBlock(true, false, false, false, true))
        // the stage follows, unless a menu or window of its own is open (the keyboard never holds it)
        assertNull(followBlock(true, false, true, false, true))
        assertEquals(FollowWait.MENU, followBlock(true, false, true, true, false))
    }

    @Test fun `a waiting song with no time limit stays, and opens as where the band is when released`() {
        val p = PendingFollow()
        assertNull(p.arrive("x", FollowWait.MENU))
        assertNull(p.release(FollowWait.KEYBOARD, "x"))
        assertEquals(FollowWait.KEYBOARD, p.reason)
        assertEquals("y", p.release(null, "y"))   // the band went elsewhere: that one opens, the strip doesn't just go
        assertNull(p.waiting)
    }
}

/** The strip while a song waits: what is drawn, in the strip's height, on a narrow phone with bigger text. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-port-xxhdpi", application = android.app.Application::class)
class FollowWaitingStripTest {
    @get:Rule val compose = createComposeRule()

    private fun strip(reason: FollowWait, song: String = "Evidências", from: String = "Voz", breakSince: Long? = null) {
        org.robolectric.RuntimeEnvironment.setFontScale(1.15f)
        compose.setContent {
            StagebookTheme {
                ConductBar(ConductUi.Following(from, song, false, false, emptyList(), emptyList(), breakSince), {}, {}, {}, {}, waiting = reason)
            }
        }
        compose.waitForIdle()
    }

    private fun assertFits() {
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        assertTrue("strip height ${root.bottom}", root.bottom <= Sizes.strip + 2.dp)
        listOf("Ir", "Sair").forEach {
            val b = compose.onNodeWithText(it).getUnclippedBoundsInRoot()
            assertTrue("\"$it\" stays whole and on screen: ${b.right}", b.right <= 360.dp && b.right - b.left > 16.dp)
        }
    }

    @Test fun `the strip tells where the band went, why it waits and offers Go and Leave`() {
        strip(FollowWait.MENU)
        compose.onNodeWithText("Voz foi para “Evidências”").assertExists()
        compose.onNodeWithText("Abre quando você fechar o menu").assertExists()
        assertFits()
    }

    @Test fun `a long title and the keyboard`() {
        strip(FollowWait.KEYBOARD, song = "Mustang Sally na versão comprida do ensaio de sábado")
        compose.onNodeWithText("Abre quando você fechar o teclado").assertExists()
        assertFits()
    }

    @Test fun `another screen and the break`() {
        strip(FollowWait.SCREEN)
        compose.onNodeWithText("Abre quando você terminar aqui").assertExists()
        assertFits()
    }

    @Test fun `on the break`() {
        strip(FollowWait.MENU, breakSince = System.currentTimeMillis())
        compose.onNodeWithText("Voz foi para o intervalo").assertExists()
        assertFits()
    }
}
