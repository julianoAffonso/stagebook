package br.app.stagebook

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.BandConduct.Notice
import br.app.stagebook.domain.Conduct
import br.app.stagebook.ui.band.ConductBar
import br.app.stagebook.ui.band.ConductUi
import br.app.stagebook.ui.band.LacksDialog
import br.app.stagebook.ui.band.text
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the strip and the leader's "What's missing" window draw for the missing songs, in Portuguese on a phone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class ConductMissingStripTest {
    @get:Rule val compose = createComposeRule()

    private val lackers = listOf(ConductUi.Lacker("b", "Baixo", listOf("Evidências", "Asa Branca")), ConductUi.Lacker("g", "Guitarra", listOf("Evidências")))
    private val lacks = lackers.map { Conduct.Lack(it.who, it.titles) }

    private fun following(missingHere: Boolean = false, noChords: Boolean = false) =
        ConductUi.Following("Voz", "Evidências", missingHere, false, emptyList(), emptyList(), noChords = noChords)

    @Test fun `the red strip draws Ask beside Leave, and it asks`() {
        var asks = 0
        compose.setContent { StagebookTheme { ConductBar(following(missingHere = true), {}, {}, {}, {}, onAsk = { asks++ }) } }
        compose.onNodeWithText("Você não tem \"Evidências\"").assertExists()
        compose.onNodeWithText("Sair").assertExists()
        compose.onNodeWithText("Pedir").assertIsEnabled().performClick()
        assertEquals(1, asks)
    }

    @Test fun `while the band answers the action is busy and can't be tapped again`() {
        var asks = 0
        compose.setContent { StagebookTheme { ConductBar(following(missingHere = true), {}, {}, {}, {}, onAsk = { asks++ }, asking = true) } }
        compose.onAllNodesWithText("Pedir").assertCountEquals(0)
        compose.onNodeWithText("Pedindo…").assertIsNotEnabled().performClick()
        assertEquals(0, asks)
    }

    @Test fun `a follower who has the song gets no Ask`() {
        compose.setContent { StagebookTheme { ConductBar(following(), {}, {}, {}, {}) } }
        compose.onNodeWithText("Sair").assertExists()
        compose.onAllNodesWithText("Pedir").assertCountEquals(0)
    }

    @Test fun `a song that came without chords says so and offers Ask`() {
        compose.setContent { StagebookTheme { ConductBar(following(noChords = true), {}, {}, {}, {}) } }
        compose.onNodeWithText("Evidências chegou sem cifra").assertExists()
        compose.onNodeWithText("Pedir").assertIsEnabled()
    }

    @Test fun `the leader's window draws each device with its action, and the one for all`() {
        val sent = mutableListOf<String?>()
        compose.setContent {
            StagebookTheme { ConductBar(ConductUi.Conducting("Show", 0, emptyList(), lacks, lackers), {}, {}, {}, {}, onSend = { sent += it }) }
        }
        compose.onNodeWithText("Ver o que falta").performClick()
        compose.onNodeWithText("O que falta").assertExists()
        compose.onNodeWithText("Baixo: Evidências, Asa Branca").assertExists()
        compose.onNodeWithText("Guitarra: Evidências").assertExists()
        compose.onNodeWithText("Enviar as 2 que faltam").performClick()
        compose.onNodeWithText("Enviar a que falta").performClick()
        compose.onNodeWithText("Enviar a todos").performClick()
        assertEquals(listOf("b", "g", null), sent)
        compose.onNodeWithText("Fechar").performClick()
        compose.onAllNodesWithText("O que falta").assertCountEquals(0)
    }

    @Test fun `with nothing missing any more the window says so and offers nothing to send`() {
        compose.setContent { StagebookTheme { LacksDialog(emptyList(), false, {}, {}) } }
        compose.onNodeWithText("Ninguém está sem música.").assertExists()
        compose.onAllNodesWithText("Enviar a todos").assertCountEquals(0)
        compose.onNodeWithText("Fechar").assertExists()
    }

    @Test fun `while the songs go out the window's actions wait`() {
        compose.setContent { StagebookTheme { LacksDialog(lackers, true, {}, {}) } }
        compose.onNodeWithText("Enviar as 2 que faltam").assertIsNotEnabled()
        compose.onNodeWithText("Enviar a todos").assertIsNotEnabled()
    }

    @Test fun `a follower's list of what is missing has nothing to send`() {
        compose.setContent { StagebookTheme { ConductBar(ConductUi.Following("Voz", "Música", false, false, listOf("Baixo"), lacks), {}, {}, {}, {}) } }
        compose.onNodeWithText("Voz", substring = true).performClick()
        compose.onNodeWithText("O que falta").assertExists()
        compose.onAllNodesWithText("Enviar a todos").assertCountEquals(0)
    }

    @Test fun `the short messages say what happened`() {
        val r = ApplicationProvider.getApplicationContext<android.app.Application>().resources
        assertEquals("Enviado para Baixo", Notice.Sent("Baixo").text(r))
        assertEquals("Enviado para 2 aparelhos da banda", Notice.SentMany(2).text(r))
        assertEquals("Evidências chegou de Voz", Notice.Arrived("Evidências", "Voz").text(r))
        assertEquals("Evidências chegou sem cifra", Notice.NoChords("Evidências").text(r))
        assertEquals("Ninguém neste Wi-Fi tem esta música", Notice.Nobody(false).text(r))
        assertEquals("Ninguém neste Wi-Fi tem esta música com cifra", Notice.Nobody(true).text(r))
    }
}
