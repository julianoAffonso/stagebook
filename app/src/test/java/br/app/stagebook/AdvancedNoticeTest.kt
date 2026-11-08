package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import br.app.stagebook.ui.settings.AdvancedDetail
import br.app.stagebook.ui.settings.AdvancedUi
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** These settings change the whole device and return on every start: the screen says so, with or without the permission. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class AdvancedNoticeTest {
    @get:Rule val compose = createComposeRule()

    private val notice = "o Stagebook grava de novo sempre que abre"

    private fun screen(canWrite: Boolean) = compose.setContent {
        StagebookTheme { AdvancedDetail(AdvancedUi(canWrite = canWrite), { _, _ -> }, {}) }
    }

    @Test fun `with the permission it warns`() {
        screen(canWrite = true)
        compose.onNodeWithText(notice, substring = true).assertExists()
    }

    @Test fun `without it too`() {
        screen(canWrite = false)
        compose.onNodeWithText(notice, substring = true).assertExists()
    }
}
