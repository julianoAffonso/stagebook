package br.app.stagebook

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
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

/** The strip of a follower who left while the leader goes on: who leads, and the one action that follows again. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class FollowAgainStripTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `the strip says who is leading and Follow follows again`() {
        var follows = 0
        var others = 0
        compose.setContent { StagebookTheme { ConductBar(ConductUi.Left("Voz"), { others++ }, { others++ }, { others++ }, { follows++ }) } }
        compose.onNodeWithText("Voz está conduzindo").assertExists()
        compose.onAllNodesWithText("Sair").assertCountEquals(0)
        compose.onNodeWithText("Acompanhar").performClick()
        assertEquals(1 to 0, follows to others)
    }
}
