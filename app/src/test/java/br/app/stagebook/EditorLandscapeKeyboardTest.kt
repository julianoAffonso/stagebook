package br.app.stagebook

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import br.app.stagebook.data.EditorQueue
import br.app.stagebook.ui.editor.SongEditorScreen
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * A phone on its side (800x360 dp) with the keyboard up (OnePlus, 14/11): the selector of the two screens goes up to the
 * title's bar, centred; the tools stay in view and the field keeps at least three lines.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w800dp-h360dp", application = android.app.Application::class)
class EditorLandscapeKeyboardTest {
    @get:Rule val compose = createComposeRule()
    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun reset() = Dispatchers.resetMain()

    private fun show(title: String) {
        val queue = EditorQueue()
        val vm = openEditorVm(title, "", (1..40).joinToString("\n") { "[G]linha $it" })
        queue.open(listOf("a", "b", "c"), 1)
        compose.setContent {
            StagebookTheme {
                // the keyboard: ~190 dp taken from the bottom (the insets reach the screen as less room)
                Box(Modifier.padding(bottom = 190.dp)) {
                    SongEditorScreen(vm.uiState, vm::onTitle, vm::onAuthor, vm::onYear, vm::onText, vm::save, {}, vm::delete, position = br.app.stagebook.ui.editor.EditorPosition(2, 12))
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun `the selector sits in the title's bar, centred, with the tools and three lines of field`() {
        show("Hazard")
        val title = compose.onNodeWithText("Hazard").getUnclippedBoundsInRoot()
        val data = compose.onNodeWithText("Dados").getUnclippedBoundsInRoot()
        val lyrics = compose.onNode(hasText("Letra e cifra") and hasClickAction() and !hasSetTextAction()).getUnclippedBoundsInRoot()
        val width = compose.onAllNodes(isRoot())[0].getUnclippedBoundsInRoot().width
        // same line as the title
        assertTrue("title $title data $data", abs((title.top + title.bottom).value / 2 - (data.top + data.bottom).value / 2) < 24)
        // centred in the bar
        val middle = (data.left + lyrics.right).value / 2
        assertTrue("selector middle $middle of ${width.value}", abs(middle - width.value / 2) < 24)
        // nothing on top of anything: the name ends before the selector
        assertTrue("title ${title.right} data ${data.left}", title.right <= data.left)
        // the tools are in view and the field keeps three lines
        compose.onNodeWithText("Verso", substring = true).assertIsDisplayed()
        val h = compose.onNode(hasSetTextAction()).getUnclippedBoundsInRoot().height
        assertTrue("field height $h", h >= 64.dp)
    }

    @Test fun `a long name is cut with an ellipsis but does not vanish or run into the selector`() {
        val long = "Uma música com um nome realmente muito comprido para a barra"
        show(long)
        val data = compose.onNodeWithText("Dados").getUnclippedBoundsInRoot()
        val title = compose.onNode(hasText(long.take(4), substring = true) and !hasClickAction() and !hasSetTextAction()).getUnclippedBoundsInRoot()
        assertTrue("title width ${title.width}", title.width >= 48.dp)
        assertTrue("title ${title.right} data ${data.left}", title.right <= data.left)
    }
}
