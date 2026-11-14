package br.app.stagebook

import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.app.stagebook.ui.band.ConductBar
import br.app.stagebook.ui.band.ConductUi
import br.app.stagebook.ui.theme.Sizes
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

    private var shown = false

    private fun show() {
        // content is set once per test; after that the states redraw it
        if (shown) return compose.waitForIdle()
        shown = true
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

    private val edge = 16.dp   // the strip's side padding
    private val textStart = edge + 24.dp + 8.dp   // after the icon and its gap

    /** A follower's two actions are beside the first line, and the missing song's title is whole under them. */
    private fun actionsBesideTitleAndSongWhole(ask: String, song: String) {
        roomy = false
        ui = following(song, missingHere = true)
        show()
        val first = bounds("conductTitleStart")
        val line = bounds("conductLineStart")
        listOf(ask, "Sair").forEach {
            val centre = (action(it).top + action(it).bottom) / 2
            assertTrue("\"$it\" is centred on the first line: $centre in ${first.top}..${first.bottom}", centre > first.top && centre < first.bottom)
            // what a finger reaches, wider than what is drawn
            val touch = compose.onNodeWithText(it).fetchSemanticsNode().touchBoundsInRoot
            assertTrue("\"$it\" is a whole touch target: ${touch.height} px", touch.height >= with(compose.density) { Sizes.touch.toPx() } - 1f)
            assertTrue("and all of it is on the strip", touch.top >= 0f)
            assertTrue("\"$it\" is inside the strip", action(it).top >= 0.dp && action(it).bottom <= stripHeight())
        }
        assertTrue("the actions end at the strip's end", action("Sair").right > 300.dp)
        assertEquals("the second line starts under the first", first.left.value, line.left.value, PIXEL)
        assertEquals("and right under it", first.bottom.value, line.top.value, PIXEL)
        compose.onNodeWithText("Você não tem \"$song\"").assertExists()
        val title = width("conductLineCut")
        roomy = true
        compose.waitForIdle()
        assertEquals("\"$song\" isn't cut", width("conductLineCut"), title, PIXEL)
    }

    @Test fun `on a phone Ask and Leave sit beside the first line and the missing song's title is whole`() {
        actionsBesideTitleAndSongWhole("Pedir", "Teste F um")
        actionsBesideTitleAndSongWhole("Pedir", "Garota de Ipanema")
    }

    @Test fun `while asking the actions sit beside the first line and the title is whole too`() {
        asking = true
        actionsBesideTitleAndSongWhole("Pedindo…", "Teste F um")
        actionsBesideTitleAndSongWhole("Pedindo…", "Garota de Ipanema")
    }

    @Test fun `the second line has the strip's width, from where the first line starts`() {
        ui = following(longTitle, missingHere = true)
        show()
        assertEquals(textStart.value, bounds("conductLineStart").left.value, PIXEL)
        // cut only at the strip's end padding: the closing quote and the arrow-less line end there
        assertEquals((360.dp - edge).value, bounds("conductLineEnd").right.value, 1f)
        assertTrue("under the actions", bounds("conductLineEnd").right > action("Pedir").left)
    }

    @Test fun `a song that came without chords has Ask and Leave beside the first line`() {
        ui = following("Teste F um", noChords = true)
        show()
        assertTrue(action("Pedir").top < bounds("conductTitleStart").top && action("Pedir").bottom > bounds("conductTitleStart").bottom)
        compose.onNodeWithText("Teste F um chegou sem cifra").assertExists()
    }

    @Test fun `a follower's strip with two actions is as tall as the leader's and close to one with one action`() {
        ui = following("Teste F um", rest = since)
        show()
        val oneAction = stripHeight()
        ui = ConductUi.Conducting("Show", 0, listOf("Baixo"), emptyList())
        compose.waitForIdle()
        val leader = stripHeight()
        listOf(following("Teste F um", missingHere = true), following("Teste F um", noChords = true), following("Teste F um", missingHere = true, rest = since)).forEach {
            ui = it
            compose.waitForIdle()
            val height = stripHeight()
            assertTrue("about the leader's ($leader): $height", height <= leader + 4.dp)
            assertTrue("no blank row over one action ($oneAction): $height", height <= oneAction + 16.dp)
            assertTrue("about 64dp: $height", height <= 68.dp)
        }
    }

    @Test fun `a long leader's name is cut on the first line and Ask and Leave keep their text`() {
        sameStrip(following("Ré", missingHere = true, from = "Voz"), following("Ré", missingHere = true, from = longName), "Pedir", "Sair")
        val name = bounds("conductTitleCut")
        assertTrue("the name ends before the actions", name.right <= action("Pedir").left)
        roomy = true
        compose.waitForIdle()
        assertTrue("and is cut", width("conductTitleCut") > (name.right - name.left).value + 20f)
    }

    @Test fun `Ask and Leave beside the first line are tapped`() {
        var asks = 0
        var leaves = 0
        compose.setContent { StagebookTheme { ConductBar(following("Teste F um", missingHere = true), {}, { leaves++ }, {}, {}, onAsk = { asks++ }) } }
        compose.onNodeWithText("Pedir").performClick()
        compose.onNodeWithText("Sair").performClick()
        assertEquals(1 to 1, asks to leaves)
    }

    // the drawn text itself, not the button around it
    private fun text(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()
    private fun icon(description: String) = compose.onNodeWithContentDescription(description)
    private fun iconBounds(description: String) = icon(description).getUnclippedBoundsInRoot()
    private val conducting = ConductUi.Conducting("Show", 1, emptyList(), emptyList())

    @Test fun `on a phone the leader's search is an icon and End is a text, both beside the first line`() {
        ui = conducting
        show()
        // "Look for the band" is no longer a written action
        compose.onAllNodesWithText("Procurar a banda").assertCountEquals(0)
        val first = bounds("conductTitleStart")
        val search = iconBounds("Procurar a banda")
        val touch = icon("Procurar a banda").fetchSemanticsNode().touchBoundsInRoot
        val least = with(compose.density) { Sizes.touch.toPx() } - 1f
        assertTrue("the icon is a touch target: ${touch.width} x ${touch.height} px", touch.width >= least && touch.height >= least)
        assertEquals("the icon is centred on the first line", (first.top.value + first.bottom.value) / 2, (search.top.value + search.bottom.value) / 2, 2f)
        // "End" whole, on one line, beside the first line
        val end = text("Encerrar")
        assertEquals("the text is centred on the first line", (first.top.value + first.bottom.value) / 2, (end.top.value + end.bottom.value) / 2, 2f)
        assertTrue("one line: ${end.bottom - end.top}", end.bottom - end.top < 24.dp)
        assertTrue("it is the last, on the strip's end: ${end.right}", end.right > 300.dp && end.right <= 360.dp)
        assertTrue("the icon is before it: ${search.right} <= ${end.left}", search.right <= end.left + 1.dp)
        // the detail line is under the title, with the width of the strip
        assertEquals(textStart.value, bounds("conductLineCut").left.value, PIXEL)
        assertTrue("the detail is under the first line", bounds("conductLineCut").top >= first.bottom - 1.dp)
    }

    @Test fun `the leader's icon searches and End ends`() {
        var searches = 0
        var ends = 0
        compose.setContent { StagebookTheme { ConductBar(conducting, { ends++ }, {}, { searches++ }, {}) } }
        icon("Procurar a banda").performClick()
        assertEquals(1 to 0, searches to ends)
        compose.onNodeWithText("Encerrar").performClick()
        assertEquals(1 to 1, searches to ends)
    }

    @Test fun `while searching a still icon takes the search icon's place and a second tap sends nothing`() {
        var searches = 0
        compose.setContent { StagebookTheme { ConductBar(conducting, {}, {}, { searches++ }, {}) } }
        val place = iconBounds("Procurar a banda")
        icon("Procurar a banda").performClick()
        compose.onAllNodesWithContentDescription("Procurar a banda").assertCountEquals(0)
        val spinner = icon("Procurando…")
        spinner.assertIsNotEnabled()
        val bounds = spinner.getUnclippedBoundsInRoot()
        assertEquals("same place", place.left.value, bounds.left.value, 1f)
        assertEquals("same size", (place.right - place.left).value, (bounds.right - bounds.left).value, 1f)
        spinner.performClick()
        assertEquals(1, searches)
    }

    @Test fun `a long setlist name is cut on the first line and the icon and End stay whole`() {
        ui = ConductUi.Conducting("Show de sábado no bar da esquina com a banda toda e mais um pouco", 1, emptyList(), emptyList())
        show()
        val name = bounds("conductTitleCut")
        val search = iconBounds("Procurar a banda")
        val end = text("Encerrar")
        assertTrue("the name ends before the icon: ${name.right} <= ${search.left}", name.right <= search.left + 1.dp)
        assertTrue("End is whole and on screen", end.right <= 360.dp && end.bottom - end.top < 24.dp)
        roomy = true
        compose.waitForIdle()
        assertTrue("and the name is cut", width("conductTitleCut") > (name.right - name.left).value + 20f)
        roomy = false
    }

    private val lacker = listOf(ConductUi.Lacker("b", "Baixo", listOf("Ré")))
    private val lacks = listOf(br.app.stagebook.domain.Conduct.Lack("Baixo", listOf("Ré")))

    /** Every state of the band's strip, for a leader and for a follower. */
    private fun everyState() = listOf(
        "conducting" to ConductUi.Conducting("Show", 0, emptyList(), emptyList()),
        "conducting, one did not answer" to conducting,
        "conducting, a song missing" to ConductUi.Conducting("Show", 0, listOf("Baixo"), lacks, lacker),
        "conducting, lacks to see" to ConductUi.Conducting("Show", 0, emptyList(), lacks, lacker),
        "following" to following("Teste F um"),
        "following, lacks to see" to ConductUi.Following("Voz", "Teste F um", false, false, emptyList(), listOf("Ré")),
        "following, song missing here" to following("Teste F um", missingHere = true),
        "following, song missing in the band" to ConductUi.Following("Voz", "Teste F um", false, false, listOf("Baixo"), emptyList()),
        "following, no chords" to following("Teste F um", noChords = true),
        "break" to following("Teste F um", rest = since),
        "break, next missing here" to following("Teste F um", missingHere = true, rest = since),
        "gone" to following("", gone = true),
        "left" to ConductUi.Left("Voz"),
    )

    @Test fun `every strip has the same height, between 60 and 64dp`() {
        everyState().forEach { (name, state) ->
            ui = state
            show()
            val height = stripHeight()
            println("STRIP_HEIGHT $name = ${height.value}")
            assertTrue("$name: $height is at least ${Sizes.strip}", height >= Sizes.strip)
            assertTrue("$name: $height is at most 64dp", height <= 64.dp)
        }
    }

    @Test @Config(qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi")
    fun `on a wide screen the leader keeps its text actions beside the text and the strip is not lower than 60dp`() {
        ui = conducting
        show()
        compose.onNodeWithText("Procurar a banda").assertExists()
        compose.onAllNodesWithContentDescription("Procurar a banda").assertCountEquals(0)
        listOf("Procurar a banda", "Encerrar").forEach { assertTrue("\"$it\" is beside the text", action(it).top < bounds("conductLineCut").bottom) }
        assertTrue("the strip is at least ${Sizes.strip}: ${stripHeight()}", stripHeight() >= Sizes.strip)
        everyState().forEach { (name, state) ->
            ui = state
            compose.waitForIdle()
            assertTrue("$name: ${stripHeight()}", stripHeight() >= Sizes.strip)
        }
    }

    @Test fun `with one action the strip is as tall as it was, the action beside the text`() {
        ui = ConductUi.Left("Voz")
        show()
        val oneLine = stripHeight()
        assertTrue(action("Acompanhar").top < bounds("conductTitleEnd").bottom)
        // two lines of text and Leave: no taller than the strip with one line
        ui = following("Teste F um", rest = since)
        compose.waitForIdle()
        assertTrue("same height: $oneLine and ${stripHeight()}", stripHeight() <= oneLine + 4.dp)
        assertTrue(action("Sair").top < bounds("conductLineStart").bottom)
    }

    @Test @Config(qualifiers = "pt-rBR-w1280dp-h800dp-land-hdpi")
    fun `on a wide screen two actions stay beside the text`() {
        ui = ConductUi.Left("Voz")
        show()
        val oneLine = stripHeight()
        ui = following("Teste F um", missingHere = true)
        compose.waitForIdle()
        assertEquals(oneLine.value, stripHeight().value, 4f)
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
        "Encerrar",
    )

    private companion object {
        val ROOMY = 1000.dp   // wide enough for the actions beside the text and every title whole
        const val PIXEL = 0.34f   // one pixel at xxhdpi, in dp
    }
}
