package br.app.stagebook

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import br.app.stagebook.ui.editor.OPEN_GUARD_MS
import br.app.stagebook.ui.editor.SongEditorScreen
import br.app.stagebook.ui.editor.SongEditorViewModel
import br.app.stagebook.ui.editor.ToolStripProbe
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The editor's tool strip is not drawn again on every typed character (audit 14/11). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w412dp-h700dp-port-xxhdpi", application = android.app.Application::class)
class EditorStripRecomposeTest {
    @get:Rule val compose = createComposeRule()
    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun reset() = Dispatchers.resetMain()

    private fun screen(vm: SongEditorViewModel) = compose.setContent {
        StagebookTheme { SongEditorScreen(vm.uiState, vm::onTitle, vm::onAuthor, vm::onYear, vm::onText, vm::save, {}, vm::delete, cursor = vm.cursor) }
    }

    private fun field(): SemanticsNodeInteraction = compose.onNode(hasSetTextAction() and hasText("Letra e cifra"))

    @Test fun `typing does not draw the tool strip again`() {
        val vm = openEditorVm(text = "[G]Hello")
        screen(vm)
        compose.waitForIdle()
        field().performTextInput("a")
        compose.waitForIdle()
        val afterOne = ToolStripProbe.drawn
        "bcdefghij".forEach { field().performTextInput(it.toString()); compose.waitForIdle() }
        assertEquals("ten more keys, no new composition of the strip", afterOne, ToolStripProbe.drawn)
    }

}
