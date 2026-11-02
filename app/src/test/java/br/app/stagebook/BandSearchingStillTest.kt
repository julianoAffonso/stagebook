package br.app.stagebook

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import br.app.stagebook.ui.band.BandActions
import br.app.stagebook.ui.band.BandDetail
import br.app.stagebook.ui.band.BandUi
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Searching for the band shows a still icon: the spinner kept the screen redrawing (~60% of a core on the TCL). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi", application = android.app.Application::class)
class BandSearchingStillTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `searching with nobody around shows no endless animation`() {
        compose.setContent { StagebookTheme { BandDetail(BandUi(name = "Tablet", wifi = true, searching = true), BandActions()) } }
        compose.onNodeWithText("Procurando", substring = true).assertExists()
        val spinners = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate))
        assertEquals(0, spinners.fetchSemanticsNodes().size)
    }
}
