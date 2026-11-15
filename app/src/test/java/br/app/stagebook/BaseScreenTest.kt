package br.app.stagebook

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.app.stagebook.ui.drawer.BaseScreen
import br.app.stagebook.ui.drawer.DrawerControl
import br.app.stagebook.ui.drawer.LocalDrawer
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The screen behind the drawer when there is no stage (user, 15/11): it says what to do and opens the drawer by itself. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-port-xxhdpi", application = android.app.Application::class)
class BaseScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun show(drawer: DrawerControl) = compose.setContent {
        StagebookTheme { CompositionLocalProvider(LocalDrawer provides drawer) { BaseScreen() } }
    }

    @Test fun `it says what to do, opens the drawer by itself and has a button to open it again`() {
        var opened = 0
        show(DrawerControl({ opened++ }, {}))
        compose.waitForIdle()
        compose.onNodeWithText("Escolha uma música").assertIsDisplayed()
        assertEquals("the drawer opens when the screen shows", 1, opened)
        compose.onNodeWithText("Abrir o menu").performClick()
        assertEquals(2, opened)
    }

    @Test fun `with the drawer pinned there is nothing to open`() {
        var opened = 0
        show(DrawerControl({ opened++ }, {}, pinned = true))
        compose.waitForIdle()
        assertEquals(0, opened)
        compose.onAllNodesWithText("Abrir o menu").assertCountEquals(0)
    }
}
