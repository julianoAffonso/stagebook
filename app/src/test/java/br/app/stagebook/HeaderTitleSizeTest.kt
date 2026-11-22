package br.app.stagebook

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import br.app.stagebook.ui.components.ScreenHeader
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The name of a setlist (an inner screen, with the arrow) keeps its size when the phone lies down (user, 16/11). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class HeaderTitleSizeTest {
    @get:Rule val compose = createComposeRule()

    private fun heightsOfTitleAndReference(): Pair<Float, Float> {
        compose.setContent {
            StagebookTheme {
                Column {
                    ScreenHeader("Show de sábado", onBack = {})
                    Text("Referência", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        compose.waitForIdle()
        return compose.onNodeWithText("Show de sábado").getBoundsInRoot().let { it.bottom.value - it.top.value } to compose.onNodeWithText("Referência").getBoundsInRoot().let { it.bottom.value - it.top.value }
    }

    @Test @Config(qualifiers = "w411dp-h891dp-port-xxhdpi")
    fun `standing, the title is the inner screen size`() {
        val (title, reference) = heightsOfTitleAndReference()
        assertEquals(reference, title, 0.5f)
    }

    @Test @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun `lying down, the title keeps the same size`() {
        val (title, reference) = heightsOfTitleAndReference()
        assertEquals(reference, title, 0.5f)
    }

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun `on a tablet too`() {
        val (title, reference) = heightsOfTitleAndReference()
        assertEquals(reference, title, 0.5f)
    }
}
