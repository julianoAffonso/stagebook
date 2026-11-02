package br.app.stagebook

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.ui.components.HomeStatus
import br.app.stagebook.ui.components.HomeStatusIcons
import br.app.stagebook.ui.components.LocalHomeStatus
import br.app.stagebook.ui.components.ScreenHeader
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The icons next to the tabs' titles: the web editor shows while it's on and opens its notice (user, 01/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class HomeStatusTest {
    @get:Rule val compose = createComposeRule()
    private val inUse = ApplicationProvider.getApplicationContext<android.app.Application>().getString(R.string.web_in_use)

    @Test fun `the web editor icon shows only while it is on and opens the notice`() {
        var opened = 0
        var address = "http://192.168.0.10:8090"
        compose.setContent {
            StagebookTheme {
                CompositionLocalProvider(LocalHomeStatus provides HomeStatus(false, 0, {}, {}, address) { opened++ }) {
                    ScreenHeader("Músicas") { HomeStatusIcons() }
                }
            }
        }
        compose.onNodeWithContentDescription(inUse).performClick()
        assertEquals(1, opened)
    }

    @Test fun `off - no web editor icon`() {
        compose.setContent {
            StagebookTheme {
                CompositionLocalProvider(LocalHomeStatus provides HomeStatus(false, 0, {}, {})) { ScreenHeader("Músicas") { HomeStatusIcons() } }
            }
        }
        compose.onAllNodesWithContentDescription(inUse).assertCountEquals(0)
    }
}
