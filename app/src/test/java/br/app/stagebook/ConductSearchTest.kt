package br.app.stagebook

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.performClick
import br.app.stagebook.ui.band.ConductBar
import br.app.stagebook.ui.band.ConductUi
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Look for the band" shows it's searching for the 30 s it lasts (user, 02/11: nothing seemed to happen). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class ConductSearchTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `the search button says searching and waits, then comes back`() {
        var searches = 0
        compose.setContent { StagebookTheme { ConductBar(ConductUi.Conducting("Teste", 0, emptyList(), emptyList()), {}, {}, { searches++ }) } }
        compose.onNodeWithText("Procurar a banda").performClick()
        compose.onNodeWithText("Procurando…").assertIsNotEnabled()
        assertEquals(1, searches)
        compose.mainClock.advanceTimeBy(31_000)
        compose.onNodeWithText("Procurar a banda").assertExists()
    }

    // on a phone the follower's only button stays on the text's line: alone underneath, it left the strip twice as tall (02/11)
    @Test fun `on a phone the follower's Leave sits beside the text`() {
        compose.setContent { StagebookTheme { ConductBar(ConductUi.Following("nxtpaper", "Música", false, false, emptyList(), emptyList()), {}, {}, {}) } }
        val text = compose.onNodeWithText("nxtpaper", substring = true).getUnclippedBoundsInRoot()
        val leave = compose.onNodeWithText("Sair").getUnclippedBoundsInRoot()
        val gap = ((leave.top + leave.bottom) / 2 - (text.top + text.bottom) / 2)
        assertTrue("Sair a $gap do meio do texto", gap < 8.dp && gap > (-8).dp)
    }
}
