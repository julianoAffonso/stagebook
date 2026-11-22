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

/** A recreated Activity (dark theme, language, font size, killed process) keeps the editor's selection (audit 14/11). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w412dp-h700dp-port-xxhdpi", application = android.app.Application::class)
class EditorRecreateTest {
    @get:Rule val compose = createComposeRule()
    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun reset() = Dispatchers.resetMain()

    private fun field(): SemanticsNodeInteraction = compose.onNode(hasSetTextAction() and hasText("Letra e cifra"))

    @Test fun `a recreated Activity keeps the selection and does not shut the touches again`() {
        val vm = openEditorVm(text = "line one\nline two", cursor = 12)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            StagebookTheme { SongEditorScreen(vm.uiState, vm::onTitle, vm::onAuthor, vm::onYear, vm::onText, vm::save, {}, vm::delete, cursor = vm.cursor) }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(OPEN_GUARD_MS + 200)
        compose.waitForIdle()
        select(3)
        assertEquals(TextRange(3), selection())
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertEquals("the selection came back", TextRange(3), selection())
        // right after the recreation: the touch is not eaten by the opening guard
        select(5)
        assertEquals(TextRange(5), selection())
    }

    private fun select(at: Int) {
        val node = field().fetchSemanticsNode()
        compose.runOnUiThread { node.config[SemanticsActions.SetSelection].action!!(at, at, true) }
        compose.waitForIdle()
    }

    private fun selection(): TextRange = field().fetchSemanticsNode().config.getOrNull(SemanticsProperties.TextSelectionRange)!!
}
