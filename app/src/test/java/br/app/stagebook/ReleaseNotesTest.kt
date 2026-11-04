package br.app.stagebook

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import br.app.stagebook.data.PublishedVersion
import br.app.stagebook.data.UpdateOffer
import br.app.stagebook.data.VersionNotes
import br.app.stagebook.ui.AppUpdateDialog
import br.app.stagebook.ui.components.ReleaseNotes
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What's new in the update window and in Settings: only what wasn't seen, in the app's language. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class ReleaseNotesTest {
    @get:Rule val compose = createComposeRule()

    private val new = VersionNotes(733, "0.1.733", en = listOf("Saving opens the stage."), pt = listOf("Salvar abre o palco."))
    private val old = VersionNotes(729, "0.1.729", en = listOf("Deleting goes back."), pt = listOf("Excluir volta."))

    @Test fun `only the versions newer than the installed one`() {
        val published = PublishedVersion(733, "0.1.733", listOf(new, old))
        assertEquals(listOf(new), published.notesSince(729))
        assertEquals(listOf(new, old), published.notesSince(700))
    }

    @Test fun `one version goes under What's new`() {
        compose.setContent { StagebookTheme { ReleaseNotes(listOf(new)) } }
        compose.onNodeWithText("O que mudou").assertExists()
        compose.onNodeWithText("Salvar abre o palco.").assertExists()
    }

    @Test fun `skipped versions go under their numbers`() {
        compose.setContent { StagebookTheme { ReleaseNotes(listOf(new, old)) } }
        compose.onNodeWithText("Versão 0.1.733").assertExists()
        compose.onNodeWithText("Versão 0.1.729").assertExists()
        compose.onNodeWithText("Excluir volta.").assertExists()
    }

    @Config(qualifiers = "en-rUS")
    @Test fun `in English the English sentences`() {
        compose.setContent { StagebookTheme { ReleaseNotes(listOf(new)) } }
        compose.onNodeWithText("What's new").assertExists()
        compose.onNodeWithText("Saving opens the stage.").assertExists()
    }

    @Test fun `without notes the window has the version and the warning`() {
        compose.setContent { StagebookTheme { AppUpdateDialog(UpdateOffer.Available("0.1.733 (abc1234)"), {}, {}, {}, {}) } }
        compose.onNodeWithText("Versão 0.1.733").assertExists()
        compose.onNodeWithText("O que mudou").assertDoesNotExist()
        compose.onNodeWithText("A banda toda", substring = true).assertExists()
    }

    @Test fun `with skipped versions the window doesn't repeat the number`() {
        compose.setContent { StagebookTheme { AppUpdateDialog(UpdateOffer.Available("0.1.733 (abc1234)", listOf(new, old)), {}, {}, {}, {}) } }
        compose.onNodeWithText("Versão 0.1.733").assertExists()   // one node: two would fail here
    }

    @Config(qualifiers = "pt-rBR-w640dp-h320dp-land")
    @Test fun `a long list on a short screen keeps the warning and the buttons`() {
        val long = VersionNotes(733, "0.1.733", pt = List(30) { "Novidade número $it" })
        compose.setContent { StagebookTheme { AppUpdateDialog(UpdateOffer.Available("0.1.733", listOf(long)), {}, {}, {}, {}) } }
        compose.onNodeWithText("A banda toda", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Atualizar").assertIsDisplayed()
    }
}
