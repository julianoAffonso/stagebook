package br.app.stagebook

import android.view.KeyEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import br.app.stagebook.ui.LocalPedalInput
import br.app.stagebook.ui.PedalInput
import br.app.stagebook.ui.stage.StageScreen
import br.app.stagebook.ui.stage.StageSetlist
import br.app.stagebook.ui.stage.StageSong
import br.app.stagebook.ui.stage.StageUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The break on a phone: a long next song keeps to one line with its key whole (the name wrapped and left the key alone
 * on a second line), and a screen reader doesn't read the song the break covers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h780dp-port-420dpi", application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)   // real text widths: the default mode measures text too narrow to overflow
class StageBreakPhoneTest {
    @get:Rule val compose = createComposeRule()

    private val songs = listOf(
        StageSong("Primeira", "", "Uma linha da primeira", false, id = "s1"),
        StageSong("Curta", "", "{key: G}\nUma linha da curta", false, id = "s2"),
        StageSong("Um nome de música comprido demais para caber na tela do celular", "", "{key: Dm}\nUma linha da comprida", false, id = "s3"),
    )
    private var song by mutableIntStateOf(0)
    private var page by mutableIntStateOf(0)
    private var since by mutableStateOf<Long?>(null)
    private val pedal = PedalInput()

    private fun show() {
        compose.setContent {
            StagebookTheme {
                CompositionLocalProvider(LocalPedalInput provides pedal) {
                    StageScreen(
                        StageUiState(loading = false, songs = songs, live = true, breaks = listOf(1, 2), setlist = StageSetlist("Show")), song, page, { s, p -> song = s; page = p },
                        breakSince = since, onBreak = { on -> since = if (on) System.currentTimeMillis() else null },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun forward() {
        compose.runOnUiThread {
            pedal.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, 0))
            pedal.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PAGE_DOWN, 0))
        }
        compose.waitForIdle()
    }

    private fun text(text: String, substring: Boolean = false) = compose.onNodeWithText(text, substring = substring, useUnmergedTree = true).assertIsDisplayed()
    private fun SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().boundsInRoot
    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val out = ArrayList<TextLayoutResult>()
        fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(out)
        return out.single()
    }

    @Test fun `a long next song is cut on one line and its key stays whole beside it`() {
        show()
        forward()
        val shortName = text("A seguir: Curta").bounds()
        val shortKey = text(" · G").bounds()
        forward(); forward()
        assertEquals(1, song)
        val name = text("A seguir: Um nome", substring = true)
        val key = text(" · Dm")
        val width = compose.onRoot().fetchSemanticsNode().boundsInRoot.width
        assertEquals("the long line is as tall as a short one", shortName.height, name.bounds().height)
        assertEquals("and sits where a short one does", shortName.top, name.bounds().top)
        assertEquals("the key on the name's line", name.bounds().top, key.bounds().top)
        assertEquals(shortKey.height, key.bounds().height)
        assertTrue("the key after the name, inside the screen: name ${name.bounds()} key ${key.bounds()}", name.bounds().right <= key.bounds().left && key.bounds().right <= width)
        assertEquals(1, name.layout().lineCount)
        assertTrue("the name is cut with an ellipsis", name.layout().isLineEllipsized(0))
        assertEquals(1, key.layout().lineCount)
        // every glyph of the key inside what the key was given
        val drawn = key.layout()
        assertFalse("the key is whole", drawn.isLineEllipsized(0))
        assertTrue("the key is whole: ${drawn.getLineRight(0)} in ${drawn.size}", drawn.getLineLeft(0) >= 0f && drawn.getLineRight(0) <= drawn.size.width && drawn.getLineEnd(0, visibleEnd = true) == " · Dm".length)
        // one sentence for a screen reader
        compose.onNode(hasText("A seguir: Um nome", substring = true) and hasText(" · Dm")).assertExists()
    }

    @Test fun `the song under the break is not read, and the setlist's handle still is`() {
        show()
        compose.onNodeWithText("Uma linha da primeira", substring = true).assertExists()
        compose.onNodeWithText("Próxima: Intervalo").assertExists()
        forward()
        compose.onNodeWithText("INTERVALO").assertIsDisplayed()
        compose.onNodeWithText("Uma linha da primeira", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Primeira").assertDoesNotExist()
        compose.onNodeWithText("Próxima: Intervalo").assertDoesNotExist()
        compose.onNodeWithContentDescription("Abrir o setlist").assertExists()
    }
}
