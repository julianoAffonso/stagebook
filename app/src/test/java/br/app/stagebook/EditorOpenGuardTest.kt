package br.app.stagebook

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextRange
import br.app.stagebook.ui.editor.OPEN_GUARD_MS
import br.app.stagebook.ui.editor.SongEditorScreen
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The editor opened from the stage (cursor >= 0, 14/11): for a moment the route's cursor wins over an IME that restarts the
 * selection and over a touch that came through from the long press; after that the person rules.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w400dp-h800dp", application = android.app.Application::class)
class EditorOpenGuardTest {
    @get:Rule val compose = createComposeRule()
    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun reset() = Dispatchers.resetMain()

    private val text = "[G]linha um\n[D]linha dois\n[C]linha tres"

    // the song's text has the chords here (profile with chords); the cursor of the route is the start of line 1: its end is 11
    private fun open(): androidx.compose.ui.test.SemanticsNodeInteraction {
        val vm = openEditorVm(text = text, cursor = 0)
        compose.mainClock.autoAdvance = false
        compose.setContent { StagebookTheme { SongEditorScreen(vm.uiState, vm::onTitle, vm::onAuthor, vm::onYear, vm::onText, vm::save, {}, vm::delete, cursor = vm.cursor) } }
        compose.mainClock.advanceTimeByFrame()
        return compose.onNode(hasSetTextAction())
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.selection() = fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]

    @Test fun `an IME that moves the selection right after opening does not win over the route's cursor`() {
        val field = open()
        val first = field.selection()
        field.performTextInputSelection(TextRange(text.length))
        compose.mainClock.advanceTimeByFrame()
        assertEquals(first, field.selection())
        compose.mainClock.advanceTimeBy(OPEN_GUARD_MS / 2)
        assertEquals(first, field.selection())
        // the person types: that is theirs
        field.performTextInput("x")
        compose.mainClock.advanceTimeByFrame()
        assertNotEquals(first, field.selection())
    }

    @Test fun `after the guard the person's selection counts`() {
        val field = open()
        val first = field.selection()
        compose.mainClock.advanceTimeBy(OPEN_GUARD_MS + 200)
        field.performTextInputSelection(TextRange(0))
        compose.mainClock.advanceTimeByFrame()
        assertEquals(TextRange(0), field.selection())
        assertNotEquals(first, field.selection())
    }

    @Test fun `a touch that comes through from the long press is ignored, a later one is not`() {
        val field = open()
        val first = field.selection()
        field.performTouchInput { click(bottomLeft.copy(x = 20f, y = bottomLeft.y - 10f)) }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(first, field.selection())
        compose.mainClock.advanceTimeBy(OPEN_GUARD_MS + 200)
        field.performTouchInput { click(bottomLeft.copy(x = 20f, y = bottomLeft.y - 10f)) }
        compose.mainClock.advanceTimeByFrame()
        assertNotEquals(first, field.selection())
    }
}
