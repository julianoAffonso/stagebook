package br.app.stagebook

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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

/** In a tall window the selector stays under the header, as in the approved drawing. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w400dp-h800dp", application = android.app.Application::class)
class EditorSelectorTallTest {
    @get:Rule val compose = createComposeRule()
    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun reset() = Dispatchers.resetMain()

    @Test fun `the selector is below the title`() {
        val vm = openEditorVm(text = "[G]linha")
        compose.setContent { StagebookTheme { SongEditorScreen(vm.uiState, vm::onTitle, vm::onAuthor, vm::onYear, vm::onText, vm::save, {}, vm::delete) } }
        compose.waitForIdle()
        val title = compose.onNodeWithText("Hazard").getUnclippedBoundsInRoot()
        val data = compose.onNodeWithText("Dados").getUnclippedBoundsInRoot()
        assertTrue("title $title data $data", data.top >= title.bottom)
    }
}
