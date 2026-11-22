package br.app.stagebook

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import br.app.stagebook.ui.components.AppIcons
import br.app.stagebook.ui.components.MenuAction
import br.app.stagebook.ui.setlists.SetlistRowUi
import br.app.stagebook.ui.setlists.SetlistsScreen
import br.app.stagebook.ui.setlists.SetlistsUiState
import br.app.stagebook.ui.setlists.setlistCardActions
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

/** V19: the setlist card has no pencil, only a ⋮ with Play, Share, Edit name and date and Delete (in this order), and tapping the card opens the setlist in the drawer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-port-xxhdpi", application = android.app.Application::class)
class SetlistCardMenuTest {
    @get:Rule val compose = createComposeRule()
    private val log = mutableListOf<String>()
    private val sets = listOf(SetlistRowUi("a", "Show de sábado", 5), SetlistRowUi("b", "Ensaio", 3))

    private fun screen(rows: List<SetlistRowUi> = sets, fontScale: Float = 1f) = compose.setContent {
        StagebookTheme {
            CompositionLocalProvider(LocalDensity provides if (fontScale == 1f) LocalDensity.current else Density(LocalDensity.current.density, fontScale)) {
                SetlistsScreen(
                    SetlistsUiState(false, rows), {}, { log += "edit:$it" }, { log += "play:$it" }, { log += "delete:$it" },
                    onRename = { id, name -> log += "rename:$id:$name" }, onShare = { log += "share:$it" },
                )
            }
        }
    }

    private fun open(n: Int = 0) = compose.onAllNodesWithContentDescription("Mais opções")[n].performClick()

    @Test fun `the card has the menu and no pencil`() {
        screen()
        compose.onAllNodesWithContentDescription("Editar setlist").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Mais opções").assertCountEquals(2)
    }

    @Test fun `the menu has Play, Share, Edit and Delete in this order, and no Lead the band`() {
        screen(); open()
        val names = listOf("Tocar", "Compartilhar", "Editar nome e data", "Apagar o setlist")
        val tops = names.map { compose.onNodeWithText(it).getUnclippedBoundsInRoot().top }
        assertEquals(tops.sorted(), tops)
        assertEquals(4, tops.toSet().size)
        compose.onAllNodesWithText("Conduzir a banda").assertCountEquals(0)
    }

    @Test fun `the options use the app's icons and Delete is the red one`() {
        var actions = emptyList<MenuAction>()
        compose.setContent { StagebookTheme { actions = setlistCardActions({}, {}, {}, {}) } }
        compose.waitForIdle()
        assertEquals(listOf(AppIcons.Play, AppIcons.Share, AppIcons.Rename, AppIcons.Delete), actions.map { it.icon })
        assertEquals(listOf(false, false, false, true), actions.map { it.danger })
    }

    @Test fun `Play opens the stage and not the setlist`() {
        screen(); open(); compose.onNodeWithText("Tocar").performClick()
        assertEquals(listOf("play:a"), log)
    }

    @Test fun `Share opens the setlist's share sheet`() {
        screen(); open(1); compose.onNodeWithText("Compartilhar").performClick()
        assertEquals(listOf("share:b"), log)
    }

    // The rename window itself (a text field) never idles inside this screen under Robolectric, which has a search field
    // too (the "New setlist" window does the same here); TextInputDialogTest covers the window, this the menu's wiring.
    @Test fun `Rename is the third option and runs the rename action`() {
        var renamed = 0
        var actions = emptyList<MenuAction>()
        compose.setContent { StagebookTheme { actions = setlistCardActions({}, {}, { renamed++ }, {}) } }
        compose.waitForIdle()
        assertEquals(listOf("Tocar", "Compartilhar", "Editar nome e data", "Apagar o setlist"), actions.map { it.label })
        actions[2].onClick()
        assertEquals(1, renamed)
    }

    @Test fun `Delete asks first and only then deletes`() {
        screen(); open(); compose.onNodeWithText("Apagar o setlist").performClick()
        assertTrue(log.isEmpty())
        compose.onNodeWithText("Apagar o setlist \"Show de sábado\"? As músicas continuam na biblioteca.", substring = true).assertExists()
        compose.onNodeWithText("Apagar").performClick()
        assertEquals(listOf("delete:a"), log)
    }

    @Test fun `Delete can be cancelled`() {
        screen(); open(); compose.onNodeWithText("Apagar o setlist").performClick()
        compose.onNodeWithText("Cancelar").performClick()
        assertTrue(log.isEmpty())
    }

    @Test fun `tapping the card opens the setlist, and the menu button does not`() {
        screen(); open()
        assertTrue(log.isEmpty())
        compose.onNodeWithText("Tocar").assertIsDisplayed()
        compose.onNodeWithText("Show de sábado").performClick()   // closes the menu
        compose.waitForIdle()
        compose.onNodeWithText("Ensaio").performClick()
        assertTrue("edit:b" in log)
    }

    @Test fun `an empty setlist still opens the edit when tapped`() {
        screen(listOf(SetlistRowUi("e", "Vazio", 0)))
        compose.onNodeWithText("Vazio").performClick()
        assertEquals(listOf("edit:e"), log)
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun `on a narrow phone with big text the long name is cut and the menu stays whole`() {
        screen(listOf(SetlistRowUi("a", "Show de sábado no clube com a banda toda e convidados especiais", 12)), fontScale = 1.15f)
        val menu = compose.onNodeWithContentDescription("Mais opções")
        menu.assertIsDisplayed()
        val touch = menu.fetchSemanticsNode().touchBoundsInRoot
        val density = compose.density.density
        assertTrue("touch target ${touch.width / density} x ${touch.height / density} dp", touch.width / density >= 47.5f && touch.height / density >= 47.5f)
        assertTrue("whole inside the screen: ${touch.right / density} dp", touch.right / density <= 360f)
    }
}
