package br.app.stagebook

import android.view.KeyEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import br.app.stagebook.ui.LocalPedalInput
import br.app.stagebook.ui.PedalInput
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * "Up next" on a narrow phone with large text: whatever the length of the name, the key never ends up alone on a
 * second line. Every name length from short to long, in Portuguese and English.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-port-420dpi", application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StageBreakLargeTextTest {
    @get:Rule val compose = createComposeRule()

    private var song by mutableIntStateOf(0)
    private var page by mutableIntStateOf(0)
    private var since by mutableStateOf<Long?>(null)
    private val pedal = PedalInput()

    private fun forward() {
        compose.runOnUiThread {
            pedal.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, 0))
            pedal.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PAGE_DOWN, 0))
        }
        compose.waitForIdle()
    }

    private fun layout(node: SemanticsNodeInteraction): TextLayoutResult {
        val out = ArrayList<TextLayoutResult>()
        node.fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(out)
        return out.single()
    }

    private fun sweep(prefix: String) {
        RuntimeEnvironment.setFontScale(1.15f)
        val words = "Um nome de música comprido demais para caber na tela do celular".split(" ")
        var songs by mutableStateOf(listOf(StageSong("Primeira", "", "Uma linha", false, id = "s1")))
        compose.setContent {
            StagebookTheme {
                CompositionLocalProvider(LocalPedalInput provides pedal) {
                    StageScreen(
                        StageUiState(loading = false, songs = songs, live = true, breaks = listOf(1), setlist = StageSetlist("Show")), song, page, { s, p -> song = s; page = p },
                        breakSince = since, onBreak = { on -> since = if (on) System.currentTimeMillis() else null },
                    )
                }
            }
        }
        for (count in 1..words.size) {
            val name = words.take(count).joinToString(" ")
            compose.runOnUiThread {
                song = 0; page = 0; since = null
                songs = listOf(songs[0], StageSong(name, "", "{key: G}\nUma linha", false, id = "s2"))
            }
            compose.waitForIdle()
            forward()
            val nameNode = compose.onNodeWithText("$prefix${name.take(3)}", substring = true, useUnmergedTree = true)
            val keyNode = compose.onNodeWithText(" · G", useUnmergedTree = true)
            val n = nameNode.fetchSemanticsNode().boundsInRoot
            val k = keyNode.fetchSemanticsNode().boundsInRoot
            assertEquals("key on the name's line for '$name'", n.top, k.top, 0.5f)
            assertEquals("one line for '$name'", 1, layout(nameNode).lineCount)
            assertEquals("key on one line for '$name'", 1, layout(keyNode).lineCount)
            assertTrue("the key is whole for '$name'", !layout(keyNode).isLineEllipsized(0))
            assertTrue("key right of name for '$name'", n.right <= k.left + 0.5f)
        }
    }

    @Config(qualifiers = "pt-rBR-w360dp-h780dp-port-420dpi")
    @Test fun `the key stays beside the name at 360 dp with large text in portuguese`() = sweep("A seguir: ")

    @Config(qualifiers = "en-w360dp-h780dp-port-420dpi")
    @Test fun `the key stays beside the name at 360 dp with large text in english`() = sweep("Up next: ")
}
