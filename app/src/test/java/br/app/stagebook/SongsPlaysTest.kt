package br.app.stagebook

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.app.stagebook.domain.PlayAgo
import br.app.stagebook.domain.PlayShow
import br.app.stagebook.domain.SongOrder
import br.app.stagebook.ui.songs.SongGroupUi
import br.app.stagebook.ui.songs.SongRowUi
import br.app.stagebook.ui.songs.SongsScreen
import br.app.stagebook.ui.songs.SongsUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Most and least played in the Songs list (user, 19/11): one field, "Show and sort", with Show, the tags and the
 * Order; by plays each row says how many times and when the last was, and the A to Z letters go.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR-w412dp-h915dp-port-xxhdpi", application = android.app.Application::class)
class SongsPlaysTest {
    @get:Rule val compose = createComposeRule()

    private val rows = listOf(
        SongRowUi("1", "Dança das Folhas", "Banda Fictícia", plays = 18, ago = PlayAgo(PlayAgo.Unit.DAYS, 2)),
        SongRowUi("2", "Caminho de Volta", "Os Inventados", plays = 1, ago = PlayAgo(PlayAgo.Unit.YESTERDAY, 1)),
        SongRowUi("3", "Céu de Brigadeiro", "Trio Imaginário"),
    )

    @Test fun `by most played each row says how many times and when, the never played says so, and no letters`() {
        compose.setContent {
            StagebookTheme { SongsScreen(SongsUiState(loading = false, songs = rows, total = 3, order = SongOrder.MOST), "", {}, {}, {}, {}, indexPinned = true) }
        }
        compose.onNodeWithText("18 vezes · última há 2 dias", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("1 vez · última ontem", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Nunca tocada", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("letter_index").assertDoesNotExist()
        compose.onNodeWithText("Todas · Mais tocadas").assertIsDisplayed()
    }

    @Test fun `in A to Z the rows have no play line`() {
        compose.setContent {
            StagebookTheme {
                SongsScreen(SongsUiState(loading = false, songs = rows, total = 3, groups = listOf(SongGroupUi("C", 0))), "", {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("18 vezes · última há 2 dias", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Todas · A a Z").assertIsDisplayed()
    }

    @Test fun `the one field opens Show, the tags and the Order, and each choice goes out`() {
        val shows = mutableListOf<PlayShow>()
        val orders = mutableListOf<SongOrder>()
        val tags = mutableListOf<Set<String>>()
        compose.setContent {
            StagebookTheme {
                SongsScreen(
                    SongsUiState(loading = false, songs = rows, total = 3, tags = listOf("Rock")), "", {}, {}, {}, {},
                    onTagsChange = { tags += it }, onShowChange = { shows += it }, onOrderChange = { orders += it },
                )
            }
        }
        compose.onNodeWithText("Mostrar e ordenar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Mostrar").assertIsDisplayed()
        compose.onNodeWithText("Ordem").assertIsDisplayed()
        compose.onNodeWithText("Nunca tocadas").performClick()
        compose.onNodeWithText("Rock").performClick()
        compose.onNodeWithText("Mais tocadas").performClick()
        assertEquals(listOf(PlayShow.NEVER), shows)
        assertEquals(listOf(setOf("Rock")), tags)
        assertEquals(listOf(SongOrder.MOST), orders)
    }

    @Test fun `with no tags in the library the field is still there, for the plays and the order`() {
        compose.setContent { StagebookTheme { SongsScreen(SongsUiState(loading = false, songs = rows, total = 3), "", {}, {}, {}, {}) } }
        compose.onNodeWithText("Mostrar e ordenar").assertIsDisplayed()
    }

    // user, 19/11: combinations that meant nothing; never played songs all have zero plays
    @Test fun `with never played the play orders are dimmed, and choosing it goes back to A to Z`() {
        val orders = mutableListOf<SongOrder>()
        var show by androidx.compose.runtime.mutableStateOf(PlayShow.ALL)
        compose.setContent {
            StagebookTheme {
                SongsScreen(
                    SongsUiState(loading = false, songs = rows, total = 3, show = show, order = SongOrder.MOST), "", {}, {}, {}, {},
                    onShowChange = { show = it }, onOrderChange = { orders += it },
                )
            }
        }
        compose.onNodeWithText("Mostrar e ordenar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Nunca tocadas").performClick()
        compose.waitForIdle()
        assertEquals(listOf(SongOrder.AZ), orders)
        compose.onNode(androidx.compose.ui.test.hasText("Mais tocadas") and androidx.compose.ui.test.hasClickAction()).assertIsNotEnabled()
        compose.onNode(androidx.compose.ui.test.hasText("Menos tocadas") and androidx.compose.ui.test.hasClickAction()).assertIsNotEnabled()
    }
}
