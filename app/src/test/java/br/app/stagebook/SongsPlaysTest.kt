package br.app.stagebook

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
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

    // user, 19/11: how to close the filter wasn't clear, and a tap outside it closed the side menu too
    @Test fun `the close on the filter's first line closes it`() {
        compose.setContent { StagebookTheme { SongsScreen(SongsUiState(loading = false, songs = rows, total = 3), "", {}, {}, {}, {}) } }
        compose.onNodeWithText("Mostrar e ordenar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Nunca tocadas").assertIsDisplayed()
        compose.onNodeWithContentDescription("Fechar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Nunca tocadas").assertDoesNotExist()
    }

    @Test fun `with the filter open, closing the side menu closes only the filter, and after it the menu closes as before`() {
        val guard = br.app.stagebook.ui.drawer.DrawerGuard()
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(br.app.stagebook.ui.drawer.LocalDrawerGuard provides guard) {
                StagebookTheme { SongsScreen(SongsUiState(loading = false, songs = rows, total = 3), "", {}, {}, {}, {}) }
            }
        }
        compose.onNodeWithText("Mostrar e ordenar").performClick()
        compose.waitForIdle()
        // what the side menu asks before closing (a tap on the dimmed stage, a swipe): kept open, the filter closes
        assertEquals(true, compose.runOnIdle { guard.asks?.invoke() })
        compose.waitForIdle()
        compose.onNodeWithText("Nunca tocadas").assertDoesNotExist()
        // the same touch, lifting on the dimmed stage right after: spent on the filter, the side menu stays (user, 19/11)
        assertEquals(true, compose.runOnIdle { guard.scrimHeld() })
        // but a song tapped right after still closes the menu as it opens the stage (audit 20/11: the menu stayed open)
        assertEquals(false, compose.runOnIdle { guard.asks?.invoke() == true })
        // a moment later, a tap outside closes the side menu as always
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(800))
        assertEquals(false, compose.runOnIdle { guard.scrimHeld() })
    }

    @Test fun `a list just opened, with the filter never touched, holds nothing`() {
        val guard = br.app.stagebook.ui.drawer.DrawerGuard()
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(br.app.stagebook.ui.drawer.LocalDrawerGuard provides guard) {
                StagebookTheme { SongsScreen(SongsUiState(loading = false, songs = rows, total = 3), "", {}, {}, {}, {}) }
            }
        }
        compose.waitForIdle()
        assertEquals(false, compose.runOnIdle { guard.asks?.invoke() == true })
        assertEquals(false, compose.runOnIdle { guard.scrimHeld() })
    }

    // the list went on following the song at its top, now somewhere in the middle (audit 20/11)
    @Test fun `changing the order starts the list at the top`() {
        val many = (1..60).map { SongRowUi("s$it", "Música $it", "", emptyList()) }
        var state by androidx.compose.runtime.mutableStateOf(SongsUiState(loading = false, songs = many, total = 60))
        compose.setContent { StagebookTheme { SongsScreen(state, "", {}, {}, {}, {}) } }
        compose.onNode(androidx.compose.ui.test.hasScrollToIndexAction()).performScrollToIndex(40)
        compose.waitForIdle()
        state = state.copy(songs = many.reversed(), order = br.app.stagebook.domain.SongOrder.MOST)
        compose.waitForIdle()
        compose.onNodeWithText("Música 60").assertIsDisplayed()
    }
}
