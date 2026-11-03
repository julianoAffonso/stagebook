package br.app.stagebook

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import br.app.stagebook.ui.home.HomeScaffold
import br.app.stagebook.ui.home.HomeTab
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A phone turned sideways switches from the bottom bar to the side rail (device test B-01): the tab's state (selection,
 * search, scroll) has to go along, not reset and come back when turned upright again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1100dp-h720dp-land-xhdpi", application = android.app.Application::class)
class HomeScaffoldStateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `the tab's saved state survives switching between the bottom bar and the side rail`() {
        var wide by mutableStateOf(false)
        compose.setContent {
            StagebookTheme {
                Box(Modifier.requiredWidth(if (wide) 900.dp else 400.dp)) {
                    HomeScaffold(HomeTab.entries.first(), {}) { m ->
                        var taps by rememberSaveable { mutableIntStateOf(0) }
                        Box(m) { Button(onClick = { taps++ }) { Text("toques $taps") } }
                    }
                }
            }
        }
        compose.onNodeWithText("toques 0").performClick()
        compose.onNodeWithText("toques 1").performClick()
        wide = true
        compose.waitForIdle()
        compose.onNodeWithText("toques 2").assertExists()
        wide = false
        compose.waitForIdle()
        compose.onNodeWithText("toques 2").assertExists()
    }

    // the logo and name on top of the tablet's rail open About (user, 03/11)
    @Test fun `tapping the rail's logo opens About`() {
        var about = 0
        compose.setContent { StagebookTheme { HomeScaffold(HomeTab.entries.first(), {}, onAbout = { about++ }) { m -> Box(m) } } }
        compose.onNodeWithText("Stagebook").performClick()
        org.junit.Assert.assertEquals(1, about)
    }
}
