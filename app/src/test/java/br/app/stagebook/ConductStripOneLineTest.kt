package br.app.stagebook

import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.app.stagebook.ui.band.ConductBar
import br.app.stagebook.ui.band.ConductUi
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The band's strip on a narrow phone with the system's text made bigger (1080 px wide, font scale 1.15; two devices,
 * 13/11): each of its two lines stays one line, the title or name is what gets cut, and the actions keep their text.
 * Real text sizes, or everything fits and nothing is checked.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h800dp-port-xxhdpi", application = android.app.Application::class)
class ConductStripOneLineTest {
    @get:Rule val compose = createComposeRule()

    private var ui by mutableStateOf<ConductUi>(ConductUi.Left("Voz"))
    private var asking by mutableStateOf(false)
    private var roomy by mutableStateOf(false)
    private val longTitle = "Mustang Sally na versão comprida do ensaio de sábado"
    private val longName = "Tablet do tecladista da banda de sábado"
    private val since = System.currentTimeMillis() - 12_300

    @Before fun bigText() = RuntimeEnvironment.setFontScale(1.15f)

    private data class Size(val width: Dp, val height: Dp)
    private fun size(text: String) = compose.onNodeWithText(text).getUnclippedBoundsInRoot().let { Size(it.right - it.left, it.bottom - it.top) }

    /** [short] and [long] are the same strip but for the title or name: same height, same actions, all on screen. */
    private fun sameStrip(short: ConductUi, long: ConductUi, vararg actions: String, busy: Boolean = false) {
        asking = busy
        ui = short
        show()
        val height = compose.onRoot().getUnclippedBoundsInRoot().bottom
        val sizes = actions.associateWith(::size)
        ui = long
        compose.waitForIdle()
        assertEquals("the strip keeps its two lines", height, compose.onRoot().getUnclippedBoundsInRoot().bottom)
        actions.forEach { action ->
            assertEquals("\"$action\" keeps its whole text on one line", sizes[action], size(action))
            val right = compose.onNodeWithText(action).getUnclippedBoundsInRoot().right
            assertTrue("\"$action\" stays on screen: $right", right <= 360.dp)
        }
    }

    private fun show() {
        // roomy: the same strip with width to spare, to learn how wide a text is when nothing cuts it
        compose.setContent {
            StagebookTheme { ConductBar(ui, {}, {}, {}, {}, if (roomy) Modifier.requiredWidth(ROOMY) else Modifier, asking = asking) }
        }
        compose.waitForIdle()
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
    // a width in dp, to compare within a pixel: the same text at another place differs by a rounding
    private fun width(tag: String) = bounds(tag).let { (it.right - it.left).value }
    private fun action(text: String) = compose.onNodeWithText(text).getUnclippedBoundsInRoot()
    private fun stripHeight() = compose.onRoot().getUnclippedBoundsInRoot().bottom

    /** The strip's two actions are under its text, and the missing song's title is drawn whole. */
    private fun actionsBelowAndTitleWhole(ask: String) {
        show()
        val line = bounds("conductLineStart")
        listOf(ask, "Sair").forEach { assertTrue("\"$it\" is under the text", action(it).top >= line.bottom) }
        assertTrue("the actions end at the strip's end", action("Sair").right > 300.dp)
        val title = width("conductLineCut")
        roomy = true
        compose.waitForIdle()
        assertEquals("the title isn't cut", width("conductLineCut"), title, PIXEL)
    }

    @Test fun `on a phone Ask and Leave go under the text and the missing song's title is whole`() {
        ui = following("Teste F um", missingHere = true)
        actionsBelowAndTitleWhole("Pedir")
    }

    @Test fun `while asking the actions are under the text and the title is whole too`() {
        ui = following("Teste F um", missingHere = true)
        asking = true
        actionsBelowAndTitleWhole("Pedindo…")
    }

    @Test fun `a song that came without chords has Ask and Leave under the text`() {
        ui = following("Teste F um", noChords = true)
        show()
        assertTrue(action("Pedir").top >= bounds("conductLineEnd").bottom)
    }

    @Test fun `with one action the strip is as tall as it was, the action beside the text`() {
        ui = ConductUi.Left("Voz")
        show()
        val oneLine = stripHeight()
        assertTrue(action("Acompanhar").top < bounds("conductTitleEnd").bottom)
        // two lines of text and Leave: no taller than the strip with one line
        ui = following("Teste F um", rest = since)
        compose.waitForIdle()
        assertEquals(oneLine, stripHeight())
        assertTrue(action("Sair").top < bounds("conductLineStart").bottom)
        // and two actions make it taller
        ui = following("Teste F um", missingHere = true)
        compose.waitForIdle()
        assertTrue(stripHeight() > oneLine)
    }

    @Test @Config(qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi")
    fun `on a wide screen two actions stay beside the text`() {
        ui = ConductUi.Left("Voz")
        show()
        val oneLine = stripHeight()
        ui = following("Teste F um", missingHere = true)
        compose.waitForIdle()
        assertEquals(oneLine, stripHeight())
        listOf("Pedir", "Sair").forEach { assertTrue(action(it).top < bounds("conductLineStart").bottom) }
    }

    private fun following(song: String, missingHere: Boolean = false, noChords: Boolean = false, rest: Long? = null, from: String = "Hi10 XPro", gone: Boolean = false) =
        ConductUi.Following(from, song, missingHere, gone, emptyList(), emptyList(), rest, noChords)

    @Test fun `the break's line cuts the next song's title, never the time`() {
        sameStrip(following("Ré", rest = since), following(longTitle, rest = since), "Sair")
        // the time, whole: as wide as it is drawn with room to spare
        val time = compose.onNodeWithTag("conductLineStart", useUnmergedTree = true).getUnclippedBoundsInRoot()
        ui = following("Ré", rest = since)
        compose.waitForIdle()
        val roomy = compose.onNodeWithTag("conductLineStart", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(roomy.right - roomy.left, time.right - time.left)
    }

    @Test fun `the break before a song that isn't here keeps one line beside Ask and Leave`() =
        sameStrip(following("Ré", missingHere = true, rest = since), following(longTitle, missingHere = true, rest = since), "Pedir", "Sair")

    @Test fun `a missing song with a long title keeps one line beside Ask and Leave`() =
        sameStrip(following("Ré", missingHere = true), following(longTitle, missingHere = true), "Pedir", "Sair")

    // "Asking…" is wider than "Ask": the second line wrapped only while the band was being asked
    @Test fun `while asking the line stays one line too`() =
        sameStrip(following("Ré", missingHere = true), following("Teste F um", missingHere = true), "Pedindo…", "Sair", busy = true)

    @Test fun `while asking a long title is cut, not the actions`() =
        sameStrip(following("Ré", missingHere = true), following(longTitle, missingHere = true), "Pedindo…", "Sair", busy = true)

    @Test fun `a long title that came without chords is cut and the line still says so`() {
        sameStrip(following("Ré", noChords = true), following(longTitle, noChords = true), "Pedir", "Sair")
        // what happened to it stays whole, after the cut title
        val end = width("conductLineEnd")
        ui = following("Ré", noChords = true)
        compose.waitForIdle()
        assertEquals(width("conductLineEnd"), end, PIXEL)
    }

    @Test fun `a long leader's name is cut beside Leave, Follow and when it is gone`() {
        sameStrip(following("Ré", from = "Voz"), following("Ré", from = longName), "Sair")
    }

    @Test fun `who left sees a long leader's name cut, and Follow whole`() = sameStrip(ConductUi.Left("Voz"), ConductUi.Left(longName), "Acompanhar")

    @Test fun `a long name of a leader that is gone is cut`() =
        sameStrip(following("", from = "Voz", gone = true), following("", from = longName, gone = true), "Sair")

    @Test fun `the leader's strip keeps its lines with a long setlist name and many names`() = sameStrip(
        ConductUi.Conducting("Show", 0, listOf("Baixo"), emptyList()),
        ConductUi.Conducting("Show de sábado no bar da esquina com a banda toda", 0, listOf("Baixo", "Guitarra", "Teclado", "Bateria", "Sopros", "Vocal de apoio"), emptyList()),
        "Procurar a banda", "Encerrar",
    )

    private companion object {
        val ROOMY = 1000.dp   // wide enough for the actions beside the text and every title whole
        const val PIXEL = 0.34f   // one pixel at xxhdpi, in dp
    }
}
