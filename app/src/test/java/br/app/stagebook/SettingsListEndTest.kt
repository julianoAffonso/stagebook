package br.app.stagebook

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.ui.settings.SettingsActions
import br.app.stagebook.ui.settings.SettingsScreen
import br.app.stagebook.ui.settings.SettingsUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The settings menu on a phone: its last item ended against the bottom bar, its edge cut by it (user, 13/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "+w412dp-h600dp")
class SettingsListEndTest {
    @get:Rule val compose = createComposeRule()
    private val ROOM = 8.dp
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun `scrolled to its end, the menu leaves room under the last item`() {
        compose.setContent { StagebookTheme { SettingsScreen(SettingsUiState(loading = false), SettingsActions()) } }
        // to the end of the list, however far that is
        repeat(4) { compose.onRoot().performTouchInput { swipeUp() } }
        compose.waitForIdle()
        // the last item (its whole card answers for its title) and the list it scrolls in
        val item = compose.onNodeWithText(context.getString(R.string.settings_about)).getUnclippedBoundsInRoot()
        val list = compose.onNode(hasScrollAction()).getUnclippedBoundsInRoot()
        val under = list.bottom - item.bottom
        assertTrue("room under the last item: $under", under >= ROOM)
    }
}
