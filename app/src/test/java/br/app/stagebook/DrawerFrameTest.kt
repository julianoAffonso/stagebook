package br.app.stagebook

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import br.app.stagebook.ui.drawer.DrawerFrame
import br.app.stagebook.ui.home.HomeTab
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import br.app.stagebook.ui.components.ScreenHeader
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The library drawer's frame: the list on top with no row of its own (user, 19/11: no logo or name), the three tabs at the
 * bottom with a small icon beside each name; the pin and the waiting requests end the tab's title line.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-port-xxhdpi", application = android.app.Application::class)
class DrawerFrameTest {
    @get:Rule val compose = createComposeRule()

    @androidx.compose.runtime.Composable
    private fun list(m: androidx.compose.ui.Modifier) = Box(m) { ScreenHeader("Canções") }

    @Test fun `the three tabs are there with their names, and no logo or app name on top`() {
        compose.setContent { StagebookTheme { DrawerFrame(HomeTab.SONGS, {}) { m -> Box(m) { Text("a lista") } } } }
        listOf("Músicas", "Setlists", "Ajustes", "a lista").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onAllNodesWithText("Stagebook").assertCountEquals(0)
    }

    @Test fun `the list starts at the top of the drawer`() {
        compose.setContent { StagebookTheme { DrawerFrame(HomeTab.SONGS, {}) { m -> Box(m) { Text("a lista") } } } }
        assertEquals(0f, compose.onNodeWithText("a lista").getUnclippedBoundsInRoot().top.value, 0.5f)
    }

    @Test fun `each tab has its name on one line, beside the icon`() {
        compose.setContent { StagebookTheme { DrawerFrame(HomeTab.SONGS, {}, badges = mapOf(HomeTab.SETLISTS to 3)) { m -> Box(m) } } }
        val name = compose.onNodeWithText("Setlists").getUnclippedBoundsInRoot()
        val badge = compose.onNodeWithText("3").getUnclippedBoundsInRoot()
        // the icon (where the badge sits) is on the name's line, not above it as before
        assertTrue("icon on the name's line", badge.bottom > name.top)
        // the bar is one line tall: a finger, not icon over name
        assertTrue("one line tall", name.bottom.value > 800f - 56f)
    }

    // real text widths: Robolectric's own measure a letter as a pixel, and the name never pushes anything
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun `the number on a tab keeps its round shape when the name is wide`() {
        compose.setContent {
            // large letters on a narrow drawer, as on a phone set to big text
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(3f, fontScale = 1.3f),
            ) {
                StagebookTheme { Box(Modifier.width(300.dp)) { DrawerFrame(HomeTab.SONGS, {}, badges = mapOf(HomeTab.SONGS to 1)) { m -> Box(m) } } }
            }
        }
        val badge = compose.onNodeWithTag(br.app.stagebook.ui.drawer.TAB_BADGE_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        // Material's badge is 16 dp across at least; squeezed, it came out 10 dp wide and 17 dp tall
        val width = (badge.right - badge.left).value
        assertTrue("the badge was squeezed to $width dp wide", width >= 16f - 0.5f)
        // and the tab with the number is wider than the others, for its name
        val named = compose.onNodeWithText("Músicas").getUnclippedBoundsInRoot()
        val other = compose.onNodeWithText("Setlists").getUnclippedBoundsInRoot()
        assertTrue("the tab with the number got no extra room", other.left.value > 300f / 3 + 5f && named.left.value < other.left.value)
    }

    @Test fun `tapping a tab says which one`() {
        val tapped = mutableListOf<HomeTab>()
        compose.setContent { StagebookTheme { DrawerFrame(HomeTab.SONGS, { tapped += it }) { m -> Box(m) } } }
        compose.onNodeWithText("Setlists").performClick()
        compose.onNodeWithText("Ajustes").performClick()
        assertEquals(listOf(HomeTab.SETLISTS, HomeTab.SETTINGS), tapped)
    }

    @Test fun `news from the band show as a number on the tab`() {
        compose.setContent { StagebookTheme { DrawerFrame(HomeTab.SONGS, {}, badges = mapOf(HomeTab.SETLISTS to 3)) { m -> Box(m) } } }
        compose.onNodeWithText("3").assertIsDisplayed()
    }

    @Test fun `the pin ends the tab's title line and says what it does`() {
        var pins = 0
        compose.setContent { StagebookTheme { DrawerFrame(HomeTab.SONGS, {}, onPin = { pins++ }) { m -> list(m) } } }
        val title = compose.onNodeWithText("Canções").getUnclippedBoundsInRoot()
        val pin = compose.onNodeWithContentDescription("Manter o menu aberto").getUnclippedBoundsInRoot()
        assertTrue("on the title's line", pin.top < title.bottom && pin.bottom > title.top)
        compose.onNodeWithContentDescription("Manter o menu aberto").performClick()
        assertEquals(1, pins)
    }

    @Test fun `a pinned drawer offers to let it close`() {
        compose.setContent { StagebookTheme { DrawerFrame(HomeTab.SONGS, {}, pinned = true, onPin = {}) { m -> list(m) } } }
        compose.onNodeWithContentDescription("Deixar o menu fechar").assertIsDisplayed()
    }

    @Test fun `without a way to pin there is no pin`() {
        compose.setContent { StagebookTheme { DrawerFrame(HomeTab.SONGS, {}) { m -> list(m) } } }
        compose.onAllNodesWithContentDescription("Manter o menu aberto").assertCountEquals(0)
    }

    @Test fun `a title outside the drawer has no pin`() {
        compose.setContent { StagebookTheme { ScreenHeader("Ajustes") } }
        compose.onAllNodesWithContentDescription("Manter o menu aberto").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Deixar o menu fechar").assertCountEquals(0)
    }

    @Test fun `with requests waiting the title line carries their number, and a tap asks to answer`() {
        var attended = 0
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                br.app.stagebook.ui.drawer.LocalWaitingRequests provides 2,
                br.app.stagebook.ui.drawer.LocalAttendRequest provides { attended++ },
            ) { StagebookTheme { DrawerFrame(HomeTab.SONGS, {}, pinned = true, onPin = {}) { m -> list(m) } } }
        }
        compose.onNodeWithContentDescription("2 pedidos esperando resposta").performClick()
        assertEquals(1, attended)
    }

    @androidx.compose.runtime.Composable
    private fun header(icons: Int) = Box(androidx.compose.ui.Modifier.width(300.dp)) {
        ScreenHeader("Setlists") {
            repeat(icons) { i -> androidx.compose.material3.IconButton({}) { androidx.compose.material3.Icon(br.app.stagebook.ui.components.AppIcons.Pin, "ícone $i") } }
        }
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun `with room the icons sit beside the title`() {
        compose.setContent { StagebookTheme { header(2) } }
        val title = compose.onNodeWithText("Setlists").getUnclippedBoundsInRoot()
        val icon = compose.onNodeWithContentDescription("ícone 0").getUnclippedBoundsInRoot()
        assertTrue("same line", icon.top < title.bottom && icon.bottom > title.top)
    }

    // real text widths: without them the title measures a pixel a letter and always fits
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun `without room the icons go up to their own line and the title stays whole under them`() {
        compose.setContent { StagebookTheme { header(6) } }
        val title = compose.onNodeWithText("Setlists").getUnclippedBoundsInRoot()
        val icon = compose.onNodeWithContentDescription("ícone 0").getUnclippedBoundsInRoot()
        assertTrue("icons above the title", icon.bottom <= title.top + 1.dp)
        // one line: the title is not broken in two
        assertTrue("one line", title.bottom - title.top < 56.dp)
    }
}
