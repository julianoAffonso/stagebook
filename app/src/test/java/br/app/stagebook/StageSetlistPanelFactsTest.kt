package br.app.stagebook

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import br.app.stagebook.data.model.Look
import br.app.stagebook.domain.SetlistShow.State
import br.app.stagebook.ui.stage.StageRowFacts
import br.app.stagebook.ui.stage.StageScreen
import br.app.stagebook.ui.stage.StageSetlist
import br.app.stagebook.ui.stage.StageSong
import br.app.stagebook.ui.stage.StageUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The setlist panel over the stage (304 dp) shows the tempo, the capo and the backing track wave as well as the key. A
 * long title is cut with an ellipsis on one line, the artist stays whole and nothing runs over the facts. Real text
 * widths (NATIVE), the text at 1.15 and both languages.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w1280dp-h800dp-land-mdpi", application = android.app.Application::class)
class StageSetlistPanelFactsTest : PanelFactsBase("Abrir o setlist")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w1280dp-h800dp-land-mdpi", application = android.app.Application::class)
class StageSetlistPanelFactsEnTest : PanelFactsBase("Open the setlist")

abstract class PanelFactsBase(private val openLabel: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val longTitle = "Faroeste Caboclo (ao vivo no Rock in Rio com a participação de todos os amigos)"
    private val songs = listOf(
        StageSong(longTitle, "", "linha", false, id = "s1", artist = "Legião Urbana"),
        StageSong("Exagerado", "", "linha", false, id = "s2", artist = "Cazuza"),
        StageSong("Sem nada", "", "linha", false, id = "s3", artist = "Banda Fictícia"),
    )
    private val setlist = StageSetlist(
        "Show", listOf(State.NOW, State.NEXT, State.NONE),
        listOf(StageRowFacts("Em", 168, 2, true), StageRowFacts("G", 112), StageRowFacts()),
    )
    private val inPanel = hasAnyAncestor(hasTestTag("stage_setlist"))

    private fun open() {
        compose.setContent {
            StagebookTheme {
                val d = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 1.15f)) {
                    StageScreen(StageUiState(loading = false, songs = songs, live = true, setlist = setlist, look = Look()), 0, 0, { _, _ -> }, onBack = {})
                }
            }
        }
        compose.onNodeWithContentDescription(openLabel).performClick()
        compose.waitForIdle()
    }

    private fun bounds(m: androidx.compose.ui.test.SemanticsMatcher) = compose.onNode(m and inPanel, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun check(bpm: String, capo: String, wave: String) {
        open()
        val panel = compose.onNode(hasTestTag("stage_setlist")).getUnclippedBoundsInRoot()
        assertEquals(304f, (panel.right - panel.left).value, 1f)
        compose.onNode(hasText("Em") and inPanel, useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasText(bpm) and inPanel, useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasText(capo) and inPanel, useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasContentDescription(wave) and inPanel, useUnmergedTree = true).assertIsDisplayed()
        val title = bounds(hasText(longTitle))
        val artist = bounds(hasText("Legião Urbana"))
        val factsLeft = bounds(hasText(bpm)).left
        assertTrue("the title stops before the facts", title.right <= factsLeft)
        assertTrue("the artist stops before the facts", artist.right <= factsLeft)
        assertTrue("the facts are inside the panel", bounds(hasText(capo)).right <= panel.right)
        // one line for the title: the row is as tall as a row whose title is short
        val longRow = bounds(hasText("Legião Urbana")).top - title.top
        val shortRow = bounds(hasText("Cazuza")).top - bounds(hasText("Exagerado")).top
        assertEquals(shortRow.value, longRow.value, 1f)
        // the artist is whole: its line is as wide as the text needs, not cut
        val full = bounds(hasText("Banda Fictícia"))
        assertTrue(full.right > full.left)
    }

    @Test fun `a long title is cut, the artist is whole and the facts show`() = check("168 bpm", "capo 2", "Backing track")
}
