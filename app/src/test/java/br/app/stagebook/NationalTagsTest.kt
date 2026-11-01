package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import br.app.stagebook.ui.components.suggestedTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** National and International only in Portuguese (user, 01/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class NationalTagsTest {
    @get:Rule val compose = createComposeRule()

    private fun suggested(): List<String> {
        var got = emptyList<String>()
        compose.setContent { got = suggestedTags() }
        compose.waitForIdle()
        return got
    }

    @Test @Config(qualifiers = "pt-rBR")
    fun `in Portuguese they are suggested`() = assertEquals(listOf("Nacional", "Internacional"), suggested())

    @Test @Config(qualifiers = "en")
    fun `in English they are not`() = assertEquals(emptyList<String>(), suggested())
}
