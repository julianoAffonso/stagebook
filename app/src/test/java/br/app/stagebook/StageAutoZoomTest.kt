package br.app.stagebook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.unit.dp
import br.app.stagebook.data.model.Look
import br.app.stagebook.data.model.StageColumns
import br.app.stagebook.domain.StageFit
import br.app.stagebook.ui.stage.StageScreen
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
 * A song nobody zoomed fills the page of this screen and that size is saved for it; a chosen zoom always wins. Real font
 * measurement: by default Robolectric gives each letter 1 px, and a squeezed or moving notice goes unnoticed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class StageAutoZoomTest {
    @get:Rule val compose = createComposeRule()

    // no section declared: 5 verses of 4 lines, more than a page at 30 sp on this phone and less than one at 22 sp
    private val verses = (1..5).joinToString("\n\n") { v -> (1..4).joinToString("\n") { "Verso $v linha $it" } }
    private val saved = ArrayList<Pair<String, Float>>()

    private fun show(zoom: Float = 1f, look: Look = Look(fontSp = 30f, columns = StageColumns.ONE)) {
        val state = StageUiState(false, listOf(StageSong("Longa", "", verses, false, id = "a", zoom = zoom)), look = look)
        compose.setContent { StagebookTheme { StageScreen(state, 0, 0, { _, _ -> }, onZoom = { id, z -> saved += id to z }) } }
        compose.waitForIdle()
    }

    private fun pages() = compose.onAllNodesWithText("2").fetchSemanticsNodes().size

    private fun pinch() {
        compose.onRoot().performTouchInput {
            pinch(center - Offset(50f, 0f), center - Offset(250f, 0f), center + Offset(50f, 0f), center + Offset(250f, 0f), durationMillis = 400)
        }
        compose.waitForIdle()
    }

    @Test fun `a song without a zoom fits the page`() {
        show()
        assertEquals(0, pages())
    }

    // the chord list sits on one page only, and on a phone on its side it can be taller than the room for the lyrics
    @Test @Config(qualifiers = "pt-rBR-w891dp-h411dp-land-xxhdpi")
    fun `the chord list doesn't take the room of every page`() {
        val chords = listOf("Am", "Dm7", "G7", "C", "F", "E7", "Bm", "D", "A", "Em", "B7", "Gm", "Cm", "Fm", "Eb")
        val song = (0..14).joinToString("\n\n") { v -> (0..3).joinToString("\n") { "[${chords[(v + it) % chords.size]}]Linha da letra $v" } }
        val look = Look(fontSp = 30f, chords = true, chordList = br.app.stagebook.data.model.ChordListPlace.TOP, columns = StageColumns.ONE)
        val state = StageUiState(false, listOf(StageSong("Longa", "", song, false, id = "a")), look = look)
        compose.setContent { StagebookTheme { StageScreen(state, 0, 0, { _, _ -> }) } }
        compose.waitForIdle()
        compose.onRoot().performTouchInput {
            pinch(center - Offset(100f, 0f), center - Offset(101f, 0f), center + Offset(100f, 0f), center + Offset(101f, 0f), durationMillis = 200)
        }
        compose.waitForIdle()
        val text = compose.onNodeWithText("Zoom em", substring = true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].first().text
        val percent = text.filter(Char::isDigit).toInt()
        assertTrue("foi ao máximo: $text", percent < 140)
    }

    @Test fun `a size calculated is kept for this screen, without the lyrics`() {
        val fits = ArrayList<br.app.stagebook.data.model.ScreenZoom>()
        val state = StageUiState(false, listOf(StageSong("Longa", "", verses, false, id = "a")), look = Look(fontSp = 30f, columns = StageColumns.ONE))
        compose.setContent { StagebookTheme { StageScreen(state, 0, 0, { _, _ -> }, onFit = { _, z -> fits += z }) } }
        compose.waitForIdle()
        assertEquals(fits.toString(), 1, fits.size)
        assertTrue(fits[0].fit, "Verso" !in fits[0].fit && fits[0].fit.isNotEmpty())
    }

    @Test fun `a size already calculated isn't measured again`() {
        var fit by androidx.compose.runtime.mutableStateOf<br.app.stagebook.data.model.ScreenZoom?>(null)
        val look = Look(fontSp = 30f, columns = StageColumns.ONE)
        val fits = ArrayList<br.app.stagebook.data.model.ScreenZoom>()
        compose.setContent {
            StagebookTheme {
                StageScreen(StageUiState(false, listOf(StageSong("Longa", "", verses, false, id = "a", fit = fit)), look = look), 0, 0, { _, _ -> }, onFit = { _, z -> fits += z })
            }
        }
        compose.waitForIdle()
        fit = fits.single().copy(zoom = 1f)   // not what the page gives: proves it comes from the song
        compose.waitForIdle()
        assertEquals(1, pages())
        assertEquals(1, fits.size)
    }

    @Test fun `changed lyrics are calculated again`() {
        val old = br.app.stagebook.data.model.ScreenZoom(1f, 1, "another song")
        val state = StageUiState(false, listOf(StageSong("Longa", "", verses, false, id = "a", fit = old)), look = Look(fontSp = 30f, columns = StageColumns.ONE))
        compose.setContent { StagebookTheme { StageScreen(state, 0, 0, { _, _ -> }) } }
        compose.waitForIdle()
        assertEquals(0, pages())
    }

    // turning the device: the app isn't recreated (configChanges), only the room of the stage changes
    @Test fun `turning the screen finds the size of the new position`() {
        var upright by androidx.compose.runtime.mutableStateOf(true)
        val fits = ArrayList<br.app.stagebook.data.model.ScreenZoom>()
        val look = Look(fontSp = 49f)
        val song = (1..6).joinToString("\n\n") { v -> "[Verso $v]\n" + (1..3).joinToString("\n") { "Uma linha da letra" } }
        val state = StageUiState(false, listOf(StageSong("Longa", "", song, false, id = "a")), look = look)
        compose.setContent {
            StagebookTheme {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.size(if (upright) 400.dp else 800.dp, if (upright) 800.dp else 400.dp)) {
                    StageScreen(state, 0, 0, { _, _ -> }, onFit = { _, z -> fits += z })
                }
            }
        }
        compose.waitForIdle()
        upright = false
        compose.waitForIdle()
        assertEquals(fits.toString(), 2, fits.size)
        assertTrue(fits.toString(), fits[0].fit != fits[1].fit)
        assertTrue("o tamanho mudou com a posição: $fits", fits[0].zoom != fits[1].zoom || fits[0].columns != fits[1].columns)
    }

    // what the stage route does: the save goes to the screen of the composition that made the lambda
    private fun turnable(): (String) -> Unit {
        var screen by androidx.compose.runtime.mutableStateOf("em pé")
        val state = StageUiState(false, listOf(StageSong("Longa", "", verses, false, id = "a")), look = Look(fontSp = 30f, columns = StageColumns.ONE))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val now = screen
            StagebookTheme { StageScreen(state, 0, 0, { _, _ -> }, screen = now, onZoom = { _, z -> saved += now to z }) }
        }
        compose.mainClock.advanceTimeBy(100)
        return { screen = it; compose.waitForIdle(); compose.mainClock.advanceTimeBy(100) }
    }

    @Test fun `a pinch right after turning goes to the new position`() {
        val turn = turnable()
        pinch()
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(listOf("em pé"), saved.map { it.first })
        turn("deitado")
        compose.onRoot().performTouchInput {
            pinch(center - Offset(250f, 0f), center - Offset(150f, 0f), center + Offset(250f, 0f), center + Offset(150f, 0f), durationMillis = 400)
        }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(listOf("em pé", "deitado"), saved.map { it.first })
    }

    @Test fun `turning ends the notice of the pinch`() {
        val turn = turnable()
        pinch()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Automático").assertIsDisplayed()
        turn("deitado")
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Automático").assertDoesNotExist()
    }

    @Test fun `a pinch not saved yet stays on the position it was made on`() {
        val turn = turnable()
        pinch()
        turn("deitado")
        compose.mainClock.advanceTimeBy(5_000)
        assertEquals(listOf("em pé"), saved.map { it.first })
    }

    // large lyrics in Settings don't fit half the screen, but the automatic size does
    @Test @Config(qualifiers = "pt-rBR-w832dp-h384dp-land-xxhdpi")
    fun `on its side a song with short lines uses two columns at its automatic size`() {
        val fits = ArrayList<br.app.stagebook.data.model.ScreenZoom>()
        val song = (1..9).joinToString("\n\n") { v -> "[Verso $v]\n" + (1..5).joinToString("\n") { "Uma linha curta da letra, com mais" } }
        val state = StageUiState(false, listOf(StageSong("Longa", "", song, false, id = "a")), look = Look(fontSp = 49f))
        compose.setContent { StagebookTheme { StageScreen(state, 0, 0, { _, _ -> }, onFit = { _, z -> fits += z }) } }
        compose.waitForIdle()
        assertEquals(fits.toString(), 2, fits.last().columns)
    }

    // lines that wrap in half the screen still take fewer pages than one column with half the screen empty
    @Test @Config(qualifiers = "pt-rBR-w832dp-h384dp-land-xxhdpi")
    fun `on its side long lines use two columns when that takes fewer pages`() {
        val fits = ArrayList<br.app.stagebook.data.model.ScreenZoom>()
        val song = (1..9).joinToString("\n\n") { v -> "[Verso $v]\n" + (1..12).joinToString("\n") { "Uma linha que passa da metade da tela no celular deitado" } }
        val state = StageUiState(false, listOf(StageSong("Longa", "", song, false, id = "a")), look = Look(fontSp = 49f))
        compose.setContent { StagebookTheme { StageScreen(state, 0, 0, { _, _ -> }, onFit = { _, z -> fits += z }) } }
        compose.waitForIdle()
        assertEquals(fits.toString(), 2, fits.last().columns)
    }

    // a pinch on its side kept the size but fell back to one column: 6 pages became 17
    @Test @Config(qualifiers = "pt-rBR-w832dp-h384dp-land-xxhdpi")
    fun `on its side a pinched size keeps two columns when that takes fewer pages`() {
        val long = (1..12).joinToString("\n\n") { v -> "Verso $v com uma linha bem comprida que passa da metade da tela do aparelho deitado\nLinha curta\nOutra curta\nMais uma" }
        val state = StageUiState(false, listOf(StageSong("Longa", "", long, false, id = "a", zoom = 0.8f)), look = Look(fontSp = 30f))
        compose.setContent { StagebookTheme { StageScreen(state, 0, 0, { _, _ -> }) } }
        compose.waitForIdle()
        val width = compose.onRoot().fetchSemanticsNode().size.width
        val second = compose.onAllNodes(androidx.compose.ui.test.hasText("Verso", substring = true)).fetchSemanticsNodes().map { it.boundsInRoot.left }
        assertTrue("colunas: $second", second.any { it > width / 2f })
    }

    @Test fun `upright a song with long lines stays in one column`() {
        val fits = ArrayList<br.app.stagebook.data.model.ScreenZoom>()
        val song = (1..9).joinToString("\n\n") { v -> "[Verso $v]\n" + (1..5).joinToString("\n") { "Uma linha bem comprida da letra que passa da metade da tela" } }
        val state = StageUiState(false, listOf(StageSong("Longa", "", song, false, id = "a")), look = Look(fontSp = 49f))
        compose.setContent { StagebookTheme { StageScreen(state, 0, 0, { _, _ -> }, onFit = { _, z -> fits += z }) } }
        compose.waitForIdle()
        assertEquals(fits.toString(), 1, fits.last().columns)
    }

    @Test fun `opening a song saves nothing`() {
        show()
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
        assertTrue(saved.toString(), saved.isEmpty())
    }

    @Test fun `the pinch badge goes back to automatic`() {
        show()
        pinch()
        compose.onNodeWithText("Automático").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals("a" to 1f, saved.last())
    }

    // the whole way, as the stage route does it: the song comes back from the repository with what was saved
    @Test fun `Automatic calculates again and the page comes back`() {
        var entry by androidx.compose.runtime.mutableStateOf<br.app.stagebook.data.model.ScreenZoom?>(null)
        val fits = ArrayList<br.app.stagebook.data.model.ScreenZoom>()
        val look = Look(fontSp = 30f, columns = StageColumns.ONE)
        compose.setContent {
            val song = StageSong("Longa", "", verses, false, id = "a", zooms = entry?.let { mapOf("s" to it) }.orEmpty()).onScreen("s")
            val state = StageUiState(false, listOf(song), look = look)
            StagebookTheme {
                StageScreen(state, 0, 0, { _, _ -> }, screen = "s",
                    onZoom = { _, z -> entry = if (z == 1f) null else br.app.stagebook.data.model.ScreenZoom(z) },
                    onFit = { _, z -> entry = z; fits += z })
            }
        }
        compose.waitForIdle()
        assertEquals(0, pages())
        pinch()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertTrue("a pinça aumentou a letra", pages() > 0)
        compose.onNodeWithText("Automático").performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals(fits.toString(), 2, fits.size)
        assertEquals(fits[0], fits[1])
        assertEquals(fits[0], entry)
        assertEquals(0, pages())
    }

    @Test fun `the badge button fits on a phone`() {
        show()
        pinch()
        val width = compose.onRoot().fetchSemanticsNode().boundsInRoot.right
        val box = compose.onNodeWithText("Automático").fetchSemanticsNode().boundsInRoot
        assertTrue("passou da borda: $box", box.right <= width + 0.5f && box.left >= 0f)
        assertTrue("espremido: $box", box.width > box.height)
    }

    @Test fun `the notice stays still while the percent changes`() {
        show()
        fun pinchBy(from: Float, to: Float) {
            compose.onRoot().performTouchInput {
                pinch(center - Offset(from, 0f), center - Offset(to, 0f), center + Offset(from, 0f), center + Offset(to, 0f), durationMillis = 400)
            }
            compose.waitForIdle()
        }
        fun place() = compose.onNodeWithText("Automático").fetchSemanticsNode().boundsInRoot.left
        fun percent() = compose.onNodeWithText("Zoom em", substring = true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].first().text
        pinchBy(250f, 200f)
        val small = place()
        val smallText = percent()
        pinchBy(100f, 300f)
        assertTrue("o percentual não mudou de tamanho: $smallText, ${percent()}", smallText.length < percent().length)
        assertEquals(small, place(), 0.5f)
    }

    @Test fun `scrolling, the badge goes back to 100 percent`() {
        show(zoom = 1.3f, look = Look(fontSp = 30f, stageMode = br.app.stagebook.data.model.StageMode.SCROLL))
        pinch()
        compose.onNodeWithText("100%").performClick()
        compose.waitForIdle()
        assertEquals("a" to 1f, saved.last())
    }

    @Test fun `scrolling, the badge has no automatic`() {
        show(look = Look(fontSp = 30f, stageMode = br.app.stagebook.data.model.StageMode.SCROLL))
        pinch()
        compose.onNodeWithText("Automático").assertDoesNotExist()
    }
}
