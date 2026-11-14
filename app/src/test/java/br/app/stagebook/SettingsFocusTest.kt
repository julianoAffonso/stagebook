package br.app.stagebook

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import br.app.stagebook.ui.components.LocalSettingsFocus
import br.app.stagebook.ui.components.SettingsFocus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tapping a search result changes the focus (and again 2.5 s later): only what reads it may recompose (render, 14/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SettingsFocusTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `a focus change does not recompose the content that does not read it`() {
        val focus = mutableStateOf<SettingsFocus?>(null)
        var content = 0
        var reader = 0
        compose.setContent {
            CompositionLocalProvider(LocalSettingsFocus provides focus) {
                SideEffect { content++ }
                Text("whole screen")
                Reader { reader++ }
            }
        }
        compose.waitForIdle()
        val before = content
        focus.value = SettingsFocus("Card", 1, "item_a")
        compose.waitForIdle()
        focus.value = SettingsFocus("Card", 2, "item_b")
        compose.waitForIdle()
        assertEquals("the screen's content stayed", before, content)
        assertEquals("the reader followed (first + two changes)", 3, reader)
        compose.onNodeWithText("item_b").assertExists()
    }
}

@androidx.compose.runtime.Composable
private fun Reader(composed: () -> Unit) {
    val f = LocalSettingsFocus.current.value
    SideEffect { composed() }
    Text(f?.item ?: "none")
}
