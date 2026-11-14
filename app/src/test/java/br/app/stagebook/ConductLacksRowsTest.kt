package br.app.stagebook

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
}
