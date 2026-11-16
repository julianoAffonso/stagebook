package br.app.stagebook

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.app.stagebook.data.BandConduct
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.Conduct
import br.app.stagebook.ui.band.ConductBar
import br.app.stagebook.ui.band.ConductUi
import br.app.stagebook.ui.band.LacksDialog
import br.app.stagebook.ui.band.followingUi
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Missing on this device" and "What's missing" list the songs as the setlist's rows: the title and, from the leader's
 * setlist, the artist. No number and no key, tempo or capo: the song isn't here, and the message carries neither.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class ConductLacksRowsTest {
    @get:Rule val compose = createComposeRule()

    private val items = listOf(Conduct.Item("Evidências", "Chitãozinho & Xororó"), Conduct.Item("Asa Branca", "Luiz Gonzaga"), Conduct.Item("Sem artista"))

    @Test fun `a follower's list has a row per missing song with its artist, from the leader's setlist`() {
        val role = BandConduct.Role.Following("voz", Conduct.Msg(Conduct.OPEN, from = "Voz", set = "Show", songs = items, index = 0), "x")
        val ui = followingUi(role, emptyList<Song>(), System.currentTimeMillis())
        assertEquals(listOf("Chitãozinho & Xororó", "Luiz Gonzaga", ""), ui.lackSongs.map { it.artist })
        compose.setContent { StagebookTheme { ConductBar(ui, {}, {}, {}, {}) } }
        compose.onNodeWithText("Ver o que falta aqui").performClick()
        compose.onNodeWithText("Faltam neste aparelho").assertIsDisplayed()
        compose.onNodeWithText("Chitãozinho & Xororó").assertIsDisplayed()
        compose.onNodeWithText("Luiz Gonzaga").assertIsDisplayed()
        // the title is a row's own text and the artist is under it
        val title = compose.onNodeWithText("Asa Branca").getUnclippedBoundsInRoot()
        val artist = compose.onNodeWithText("Luiz Gonzaga").getUnclippedBoundsInRoot()
        assertTrue("the artist is under the title", artist.top >= title.bottom - 1f.dp)
        compose.onAllNodesWithText("Sem artista").assertCountEquals(1)
    }

    @Test fun `the leader's window lists each device with its songs as rows, with their artists`() {
        val lackers = listOf(
            ConductUi.Lacker("b", "Baixo", listOf("Evidências", "Asa Branca"), listOf(ConductUi.LackSong("Evidências", "Chitãozinho & Xororó"), ConductUi.LackSong("Asa Branca", "Luiz Gonzaga"))),
            ConductUi.Lacker("g", "Guitarra", listOf("Asa Branca")),
        )
        compose.setContent { StagebookTheme { LacksDialog(lackers, false, {}, {}) } }
        compose.onNodeWithText("Baixo").assertIsDisplayed()
        compose.onNodeWithText("Guitarra").assertIsDisplayed()
        compose.onNodeWithText("Chitãozinho & Xororó").assertIsDisplayed()
        // not the old "who: title, title" line
        compose.onAllNodesWithText("Baixo: ", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Evidências, Asa Branca", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Enviar as 2 que faltam").assertIsDisplayed()
    }

    @Test fun `a follower's list offers to ask the band, ask the leader online and import the missing ones, as a shared setlist does`() {
        val role = BandConduct.Role.Following("voz", Conduct.Msg(Conduct.OPEN, from = "Voz", set = "Show", songs = items, index = 0), "x")
        val ui = followingUi(role, emptyList<Song>(), System.currentTimeMillis())
        var band = 0; var internet = 0; var import = 0
        compose.setContent {
            StagebookTheme {
                ConductBar(ui, {}, {}, {}, {}, options = br.app.stagebook.ui.band.AskOptions(bandDevices = 2, internetFrom = "Voz"),
                    onAskBand = { band++ }, onAskInternet = { internet++ }, onImportMissing = { import++ })
            }
        }
        compose.onNodeWithText("Ver o que falta aqui").performClick()
        compose.onNodeWithText("Pedir à banda (2 aparelhos na rede)").performClick()
        compose.onNodeWithText("Pedir as que faltam a Voz pela internet").performClick()
        // the online request closes the window (its notice shows whole): opened again for the import
        compose.onAllNodesWithText("Faltam neste aparelho").assertCountEquals(0)
        compose.onNodeWithText("Ver o que falta aqui").performClick()
        compose.onNodeWithText("Importar as que faltam (3)").performClick()
        assertEquals(listOf(1, 1, 1), listOf(band, internet, import))
    }

    @Test fun `the copy to ask for is picked from a list that opens, and the request says it`() {
        val role = BandConduct.Role.Following("voz", Conduct.Msg(Conduct.OPEN, from = "Voz", set = "Show", songs = items, index = 0), "x")
        val ui = followingUi(role, emptyList<Song>(), System.currentTimeMillis())
        val asked = mutableListOf<String>()
        compose.setContent {
            StagebookTheme { ConductBar(ui, {}, {}, {}, {}, options = br.app.stagebook.ui.band.AskOptions(bandDevices = 1), onAskBand = { asked += it }) }
        }
        compose.onNodeWithText("Ver o que falta aqui").performClick()
        // closed, it says its label and what is chosen: each song as the band plays it
        compose.onNodeWithText("Qual cópia pedir").assertIsDisplayed()
        compose.onNodeWithText("Como a banda toca").assertIsDisplayed()
        compose.onAllNodesWithText("Só letra").assertCountEquals(0)
        compose.onNodeWithText("Pedir à banda (1 aparelho na rede)").performClick()
        compose.onNodeWithText("Como a banda toca").performClick()
        compose.onNodeWithText("PDF").performClick()
        compose.onAllNodesWithText("Como a banda toca").assertCountEquals(0)
        compose.onNodeWithText("Pedir à banda (1 aparelho na rede)").performClick()
        assertEquals(listOf("", "pdf"), asked)
    }

    @Test fun `what just happened is said in the strip, whole, in place of its lines`() {
        val role = BandConduct.Role.Following("voz", Conduct.Msg(Conduct.OPEN, from = "Voz", set = "Show", songs = items, index = 0), "x")
        val ui = followingUi(role, emptyList<Song>(), System.currentTimeMillis())
        var notice by mutableStateOf<String?>(null)
        val text = "Pedido enviado a Voz. As músicas chegam em Recebidos da banda quando a pessoa abrir o Stagebook."
        compose.setContent { StagebookTheme { ConductBar(ui, {}, {}, {}, {}, notice = notice) } }
        compose.onAllNodesWithTag("conduct-notice").assertCountEquals(0)
        compose.onNodeWithText("Ver o que falta aqui").assertIsDisplayed()
        notice = text
        compose.onNodeWithText(text).assertIsDisplayed()
        compose.onAllNodesWithText("Ver o que falta aqui").assertCountEquals(0)
        // the sentence is inside the strip, not over what is above it
        val strip = compose.onNodeWithTag("conduct-notice").getUnclippedBoundsInRoot()
        val said = compose.onNodeWithText(text).getUnclippedBoundsInRoot()
        assertTrue("the sentence fits in the strip", said.top >= strip.top && said.bottom <= strip.bottom && said.left >= strip.left && said.right <= strip.right)
        notice = null
        compose.onNodeWithText("Ver o que falta aqui").assertIsDisplayed()
    }

    @Test fun `with nobody on the network and no online leader the list offers only the import`() {
        val role = BandConduct.Role.Following("voz", Conduct.Msg(Conduct.OPEN, from = "Voz", set = "Show", songs = items, index = 0), "x")
        val ui = followingUi(role, emptyList<Song>(), System.currentTimeMillis())
        compose.setContent { StagebookTheme { ConductBar(ui, {}, {}, {}, {}) } }
        compose.onNodeWithText("Ver o que falta aqui").performClick()
        compose.onAllNodesWithText("Pedir à banda", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("pela internet", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Importar as que faltam (3)").assertIsDisplayed()
    }

    @Test fun `a song that is not here shows Ask even while the strip waits, and not Go`() {
        val role = BandConduct.Role.Following("voz", Conduct.Msg(Conduct.OPEN, from = "Voz", set = "Show", songs = items, index = 0), null)
        val ui = followingUi(role, emptyList<Song>(), System.currentTimeMillis())
        compose.setContent { StagebookTheme { ConductBar(ui, {}, {}, {}, {}, waiting = br.app.stagebook.domain.FollowWait.OTHER_SONG) } }
        compose.onNodeWithText("Pedir").assertExists()   // the current song is not here (no song id)
        compose.onAllNodesWithText("Ir").assertCountEquals(0)
    }
}
