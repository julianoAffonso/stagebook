package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import br.app.stagebook.domain.MidiKind
import br.app.stagebook.domain.SongMidi
import br.app.stagebook.ui.editor.MidiDialog
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The song's MIDI window (user, 07/11): messages on entering and leaving, held to MIDI's ranges. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class MidiDialogTest {
    @get:Rule val compose = createComposeRule()
    private var done: SongMidi? = null

    private fun open(midi: SongMidi = SongMidi()) = compose.setContent { StagebookTheme { MidiDialog(midi, { done = it }, {}) } }

    @Test fun `a program change on entering, by default`() {
        open()
        compose.onNodeWithText("Nada é enviado.").assertExists()
        compose.onNodeWithText("Adicionar mensagem").performClick()
        compose.onNodeWithText("OK").performClick()
        assertEquals(MidiKind.PROGRAM, done!!.enter.single().kind)
        assertEquals(emptyList<Any>(), done!!.exit)
    }

    @Test fun `on leaving has its own list`() {
        open()
        compose.onNodeWithText("Ao sair").performClick()
        compose.onNodeWithText("Adicionar mensagem").performClick()
        compose.onNodeWithText("OK").performClick()
        assertEquals(1, done!!.exit.size)
        assertEquals(0, done!!.enter.size)
    }

    @Test fun `a number past the range stops at its end`() {
        open()
        compose.onNodeWithText("Adicionar mensagem").performClick()
        compose.onAllNodesWithText("Programa")[0].performTextReplacement("300")
        compose.onNodeWithText("OK").performClick()
        assertEquals(128, done!!.enter.single().number)
    }
}
