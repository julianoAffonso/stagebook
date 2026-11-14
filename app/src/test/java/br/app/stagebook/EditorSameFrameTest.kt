package br.app.stagebook

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import br.app.stagebook.ui.editor.OPEN_GUARD_MS
import br.app.stagebook.ui.editor.SongEditorScreen
import br.app.stagebook.ui.editor.SongEditorViewModel
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

/** Two onValueChange in one frame (the IME batching) leave the model with the last text (audit 14/11). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w412dp-h700dp-port-xxhdpi", application = android.app.Application::class)
class EditorSameFrameTest {
    @get:Rule val compose = createComposeRule()
    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun reset() = Dispatchers.resetMain()

    private fun screen(vm: SongEditorViewModel) = compose.setContent {
        StagebookTheme { SongEditorScreen(vm.uiState, vm::onTitle, vm::onAuthor, vm::onYear, vm::onText, vm::save, {}, vm::delete, cursor = vm.cursor) }
    }

    private fun field(): SemanticsNodeInteraction = compose.onNode(hasSetTextAction() and hasText("Letra e cifra"))

    @Test fun `two changes in the same frame leave the model with the last one`() {
        val vm = openEditorVm(text = "ab")
        screen(vm)
        compose.waitForIdle()
        // the IME batching: "c" typed and deleted before the screen is drawn again
        val node = field().fetchSemanticsNode()
        compose.runOnUiThread {
            val set = node.config[SemanticsActions.SetText].action!!
            set(AnnotatedString("abc"))
            set(AnnotatedString("ab"))
        }
        compose.waitForIdle()
        assertTrue("the model has '${vm.uiState.text}'", vm.uiState.text.endsWith("ab") && !vm.uiState.text.contains("abc"))
    }

}
