package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import br.app.stagebook.ui.components.ScreenHeader
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tapping a setlist's name renames it: only the name, not the empty room after it (audit 03/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class ScreenHeaderTapTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `the title's tap area is the name, not the whole row`() {
        compose.setContent { StagebookTheme { ScreenHeader("Show", onBack = {}, onTitleClick = {}) } }
        val width = compose.onNodeWithText("Show").fetchSemanticsNode().boundsInRoot.width
        val density = compose.density.density
        assertTrue("tap area $width px", width < 150.dp.value * density)
    }
}
