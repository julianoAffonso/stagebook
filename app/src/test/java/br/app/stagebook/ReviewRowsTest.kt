package br.app.stagebook

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.app.stagebook.ui.setlists.ScanRow
import br.app.stagebook.ui.setlists.ScanSetlistScreen
import br.app.stagebook.ui.setlists.ScanUi
import br.app.stagebook.ui.setlists.SongRef
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The review of what arrived (setlist, and loose songs) draws each song as the setlist's row on top of its card: small
 * number, title, artist and, when the library has the song, its key, tempo, capo and track. What happens to it and the
 * controls are on a line below, in the same card. The candidates of an uncertain song are rows too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w412dp-h915dp-port-xxhdpi", application = android.app.Application::class)
class ReviewRowsTest {
    @get:Rule val compose = createComposeRule()

    private val mine = SongRef("1", "Evidências", "Chitãozinho & Xororó", "G", 72, 2, true)
    private val rows = listOf(
        ScanRow("Evidências", "Chitãozinho & Xororó", true, listOf(mine), "1"),
        ScanRow("Trem-Bala", "Ana Vilela", false, listOf(SongRef("2", "Trem Bala", "Ana Vilela", "A", 84), SongRef("3", "Trem-Bala (ao vivo)", "Ana Vilela", "Bm", 90, 0, true)), null),
        ScanRow("Sumida", "Banda Fictícia", false, emptyList(), null),
    )
    private var chosen: Pair<Int, String?>? = null

    private fun show(review: ScanUi.Review) = compose.setContent {
        StagebookTheme { ScanSetlistScreen(review, true, {}, {}, {}, { i, id -> chosen = i to id }, {}, { null }, {}, {}, canScanAgain = false) }
    }

    @Test fun `each song is the setlist's row on top, with its number, artist and facts, and the state under it`() {
        show(ScanUi.Review("Show", rows))
        for (n in listOf("1", "2", "3")) compose.onNodeWithText(n, useUnmergedTree = true).assertIsDisplayed()
        for (t in listOf("Evidências", "Trem-Bala", "Sumida")) compose.onNodeWithText(t, useUnmergedTree = true).assertIsDisplayed()
        // the artist shows on every row (the second review had none)
        compose.onNodeWithText("Chitãozinho & Xororó", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Ana Vilela", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Banda Fictícia", useUnmergedTree = true).assertIsDisplayed()
        // the facts of the song the library has; none for the one that isn't there
        compose.onNodeWithText("G", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("72 bpm", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("capo 2", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Backing track", useUnmergedTree = true).assertCountEquals(1)
        // the state is on a line under the title
        val title = compose.onNodeWithText("Evidências", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val state = compose.onNodeWithText("Encontrada na sua biblioteca", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the state is under the title", state.top >= title.bottom)
        compose.onNodeWithText("2 candidatas", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Escolher", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Não está na sua biblioteca e vai ficar de fora.", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun `the candidates are rows too, with the artist and the facts, and picking one chooses it`() {
        show(ScanUi.Review("Show", rows))
        compose.onNodeWithText("Escolher", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Trem Bala", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Trem-Bala (ao vivo)", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("84 bpm", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Bm", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Deixar de fora", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Trem Bala", useUnmergedTree = true).performClick()
        assertEquals(1 to "2", chosen)
    }

    @Test fun `loose songs received show the number, the artist and the state under the title`() {
        val loose = listOf(
            ScanRow("Nova", "Trio Imaginário", false, emptyList(), null, carried = true),
            ScanRow("Evidências", "Chitãozinho & Xororó", true, listOf(mine), "1"),
        )
        show(ScanUi.Review("", loose, songsOnly = true))
        compose.onNodeWithText("Trio Imaginário", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Chitãozinho & Xororó", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("1", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("2", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("72 bpm", useUnmergedTree = true).assertIsDisplayed()
        val title = compose.onNodeWithText("Nova", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val state = compose.onNodeWithText("Nova, entra na sua biblioteca", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the state is under the title", state.top >= title.bottom)
        assertEquals(1, loose.count { it.carried })
        compose.onAllNodesWithText("Você já tem, fica a sua", useUnmergedTree = true).assertCountEquals(1)
    }
}
