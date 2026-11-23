package br.app.stagebook

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.app.stagebook.data.BandInbox
import br.app.stagebook.ui.setlists.PickRowUi
import br.app.stagebook.ui.setlists.PickSongsScreen
import br.app.stagebook.ui.setlists.PickSongsUiState
import br.app.stagebook.ui.share.InboxDialog
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Review of the help's screenshots (user, 19/11): the song picker had the old back arrow and its count covered the check;
 * in "Received from the band" another setlist sat under "2 versions" and read as a third one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w411dp-h891dp-port-xxhdpi", application = android.app.Application::class)
class PickAndInboxLayoutTest {
    @get:Rule val compose = createComposeRule()

    private val rows = listOf(PickRowUi("1", "Primeira", "A", inSet = false, selected = true, order = 1), PickRowUi("2", "Segunda", "B", inSet = true, selected = false))

    @Test fun `the picker goes back by the setlist's name above its title, with no arrow`() {
        var back = 0
        compose.setContent { StagebookTheme { PickSongsScreen(PickSongsUiState(setName = "Show de sábado", rows = rows, selectedCount = 1, inSetCount = 1), "", {}, {}, {}, { back++ }) } }
        compose.onAllNodesWithContentDescription("Voltar").assertCountEquals(0)
        compose.onNodeWithText("Adicionar músicas").assertExists()
        compose.onNodeWithText("‹ Show de sábado").performClick()
        assertEquals(1, back)
    }

    @Test fun `the count sits beside the check, not over it`() {
        compose.setContent { StagebookTheme { PickSongsScreen(PickSongsUiState(setName = "Show", rows = rows, selectedCount = 1, inSetCount = 1), "", {}, {}, {}, {}) } }
        val check = compose.onNodeWithContentDescription("Adicionar (1)", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val count = compose.onNodeWithText("2", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the count is right of the check", count.left >= check.right)
    }

    private fun item(id: String, from: String, set: String, at: Long) = BandInbox.Item(id, id, from, "STAGEBOOK1\n8\n$set", at = at)

    @Test fun `another setlist is not under the versions of one, and the explanation sits with its group`() {
        val now = System.currentTimeMillis()
        val items = listOf(item("1", "Tablet do Beto", "Show", now), item("2", "Celular da Ana", "Show", now - 60_000), item("3", "Celular do Caio", "Ensaio", now - 120_000))
        compose.setContent { StagebookTheme { InboxDialog(items, {}, {}, {}) } }
        val ana = compose.onNodeWithText("Celular da Ana").getUnclippedBoundsInRoot()
        val hint = compose.onNodeWithText("Mais de uma pessoa", substring = true).getUnclippedBoundsInRoot()
        val other = compose.onNodeWithText("Ensaio", substring = true).getUnclippedBoundsInRoot()
        assertTrue("the explanation right under its group", hint.top >= ana.bottom && hint.bottom <= other.top)
    }
}
