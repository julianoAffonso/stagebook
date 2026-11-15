package br.app.stagebook

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.app.stagebook.ui.drawer.DrawerFrame
import br.app.stagebook.ui.home.HomeTab
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The library drawer's frame (user, 15/11): name and version on top, the list in the middle, the three tabs with icon and name at the bottom. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-port-xxhdpi", application = android.app.Application::class)
class DrawerFrameTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `the three tabs are there with their names and the list sits between`() {
        compose.setContent { StagebookTheme { DrawerFrame(HomeTab.SONGS, {}) { m -> Box(m) { Text("a lista") } } } }
        listOf("Músicas", "Setlists", "Ajustes", "Stagebook", "a lista").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        // the logo and the name only: no version under it
        compose.onNode(androidx.compose.ui.test.hasText("0.1", substring = true)).assertDoesNotExist()
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

    @Test fun `the logo and name open About`() {
        var about = 0
        compose.setContent { StagebookTheme { DrawerFrame(HomeTab.SONGS, {}, onAbout = { about++ }) { m -> Box(m) } } }
        compose.onNode(hasText("Stagebook")).performClick()
        assertEquals(1, about)
    }

    @Test fun `the pin shows only when it can be used and says what it does`() {
        var pins = 0
        compose.setContent { StagebookTheme { DrawerFrame(HomeTab.SONGS, {}, onPin = { pins++ }) { m -> Box(m) } } }
        compose.onNodeWithContentDescription("Manter o menu aberto").performClick()
        assertEquals(1, pins)
    }

    @Test fun `a pinned drawer offers to let it close`() {
        compose.setContent { StagebookTheme { DrawerFrame(HomeTab.SONGS, {}, pinned = true, onPin = {}) { m -> Box(m) } } }
        compose.onNodeWithContentDescription("Deixar o menu fechar").assertIsDisplayed()
    }

    @Test fun `without a way to pin there is no pin`() {
        compose.setContent { StagebookTheme { DrawerFrame(HomeTab.SONGS, {}) { m -> Box(m) } } }
        compose.onAllNodesWithContentDescription("Manter o menu aberto").assertCountEquals(0)
    }
}
