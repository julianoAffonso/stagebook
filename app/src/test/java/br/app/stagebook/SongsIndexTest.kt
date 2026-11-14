package br.app.stagebook

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import br.app.stagebook.domain.SongGroups
import br.app.stagebook.domain.TextKey
import br.app.stagebook.ui.songs.SongGroupUi
import br.app.stagebook.ui.songs.SongRowUi
import br.app.stagebook.ui.songs.SongsScreen
import br.app.stagebook.ui.songs.SongsUiState
import br.app.stagebook.ui.theme.StagebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The song list's rows and its A to Z index (user, 13/11). A row ends with the song's key, tempo, capo and backing
 * track mark, as a setlist's rows do. A long list has a letter before each group and, while it is scrolled, the letters
 * at the edge: a tap on one takes the list there.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)   // real text widths: a long title has to be cut
@Config(sdk = [35], qualifiers = "pt-rBR-w412dp-h915dp-port-xxhdpi", application = android.app.Application::class)
class SongsIndexTest {
    @get:Rule val compose = createComposeRule()

    /** The state the view model builds for these titles: title order, then the groups. */
    private fun state(titles: List<String>, row: (Int, String) -> SongRowUi = { i, t -> SongRowUi("s$i", t, "Banda Fictícia") }): SongsUiState {
        val grouped = SongGroups.group(TextKey.sorted(titles) { it }.mapIndexed(row)) { SongGroups.letter(it.title) }
        return SongsUiState(loading = false, songs = grouped.rows, total = titles.size, groups = grouped.groups.map { SongGroupUi(it.letter, it.first) })
    }

    // 62 songs: three of each letter from A to T, one that starts with a digit and one with an accent
    private val letters = ('A'..'T').map { it.toString() }
    private val long = state(letters.flatMap { l -> (1..3).map { "$l canção $it" } } + "9 Luas" + "Élan")

    private fun show(state: SongsUiState, pinned: Boolean = false, selection: List<String> = emptyList()) {
        compose.setContent { StagebookTheme { SongsScreen(state, "", {}, {}, {}, {}, indexPinned = pinned, initialSelection = selection) } }
        compose.waitForIdle()
    }

    private fun index() = compose.onNodeWithTag("letter_index")
    private fun indexLetter(letter: String) = compose.onNodeWithTag("letter_index_$letter", useUnmergedTree = true)
    private fun header(letter: String) = compose.onNodeWithTag("letter_header_$letter")
    private fun bounds(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot

    // ---- the rows ----
    @Test fun `a row draws the key, the tempo, the capo and the backing track mark, and only what the song has`() {
        show(SongsUiState(
            loading = false, total = 3,
            songs = listOf(
                SongRowUi("1", "Com tudo", "Banda Fictícia", songKey = "Em", bpm = 120, capo = 2, hasTrack = true),
                SongRowUi("2", "Só o tom", "Trio Imaginário", songKey = "G"),
                SongRowUi("3", "Sem nada", "Banda Fictícia"),
            ),
        ))
        compose.onNodeWithText("Em").assertIsDisplayed()
        compose.onNodeWithText("G").assertIsDisplayed()
        compose.onNodeWithText("120 bpm").assertIsDisplayed()
        compose.onNodeWithText("capo 2").assertIsDisplayed()
        compose.onAllNodesWithText("bpm", substring = true).assertCountEquals(1)
        compose.onAllNodesWithText("capo", substring = true).assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Backing track").assertCountEquals(1)
        // the key at the row's end, the rest under it
        val title = bounds("Com tudo")
        val key = bounds("Em")
        val bpm = bounds("120 bpm")
        assertTrue("title $title key $key", title.right <= key.left)
        assertTrue("key $key bpm $bpm", key.bottom <= bpm.top)
        // the artist under the title, alone
        compose.onAllNodesWithText("Banda Fictícia", useUnmergedTree = true).assertCountEquals(2)
        compose.onNodeWithText("Trio Imaginário", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun `a long title stays on one line and leaves the key in sight`() {
        show(SongsUiState(
            loading = false, total = 2,
            songs = listOf(
                SongRowUi("1", "Um título comprido demais para caber inteiro na linha de um celular qualquer", "Banda Fictícia", songKey = "F#m", bpm = 96, capo = 3, hasTrack = true),
                SongRowUi("2", "Curta", "Banda Fictícia", songKey = "G"),
            ),
        ))
        val long = bounds("Um título comprido demais para caber inteiro na linha de um celular qualquer")
        val short = bounds("Curta")
        val key = bounds("F#m")
        assertEquals("one line, as the short title", short.height, long.height, 1f)
        assertTrue("title $long key $key", long.right <= key.left)
        assertEquals("the keys line up at the rows' end", bounds("G").right, key.right, 1f)
        compose.onNodeWithText("96 bpm").assertIsDisplayed()
        compose.onNodeWithText("capo 3").assertIsDisplayed()
    }

    // ---- the letters ----
    @Test fun `a short list is a plain list - no letter before the songs and no index, even scrolled`() {
        val short = state(('A'..'S').map { "$it canção" })   // 19 songs
        show(short, pinned = true)
        assertEquals(emptyList<SongGroupUi>(), short.groups)
        index().assertDoesNotExist()
        header("A").assertDoesNotExist()
        compose.onNodeWithText("A canção").assertIsDisplayed()
    }

    @Test fun `a long list has a letter before each group, with the digits first`() {
        show(long)
        header("#").assertIsDisplayed()
        header("A").assertIsDisplayed()
        val other = header("#").fetchSemanticsNode().boundsInRoot
        val digit = compose.onNodeWithText("9 Luas").fetchSemanticsNode().boundsInRoot
        val a = header("A").fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithText("A canção 1").fetchSemanticsNode().boundsInRoot
        assertTrue("# $other, its song $digit, A $a, its song $first", other.bottom <= digit.top && digit.bottom <= a.top && a.bottom <= first.top)
    }

    @Test fun `the index lists only the letters the list has, in order`() {
        show(long, pinned = true)
        index().assertIsDisplayed()
        val tops = (listOf("#") + letters).map { indexLetter(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops)
        listOf("U", "V", "X", "Z", "É").forEach { indexLetter(it).assertDoesNotExist() }
    }

    @Test fun `the index follows the filter - fewer letters with fewer songs, and none when too few are left`() {
        // what a search for "canção 1" and "canção 2" leaves: 40 songs of 20 letters, no digit
        val filtered = state(letters.flatMap { l -> (1..2).map { "$l canção $it" } })
        show(filtered, pinned = true)
        indexLetter("#").assertDoesNotExist()
        indexLetter("A").assertIsDisplayed()
        indexLetter("T").assertIsDisplayed()
    }

    @Test fun `the index only shows while the list is scrolled, and goes a moment after`() {
        compose.mainClock.autoAdvance = false
        compose.setContent { StagebookTheme { SongsScreen(long, "", {}, {}, {}, {}) } }
        compose.mainClock.advanceTimeBy(500)
        index().assertDoesNotExist()
        header("A").assertIsDisplayed()   // the letters in the list are always there
        compose.onNodeWithText("A canção 2").performTouchInput { down(center); moveBy(Offset(0f, -200f)); moveBy(Offset(0f, -200f)) }
        compose.mainClock.advanceTimeBy(300)
        index().assertIsDisplayed()
        indexLetter("M").assertIsDisplayed()
        compose.onNodeWithText("A canção 2").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1_000)
        index().assertIsDisplayed()   // still there a second after the finger left
        compose.mainClock.advanceTimeBy(1_500)
        index().assertDoesNotExist()
    }

    @Test fun `a tap on a letter of the index brings that letter's header to the top of the list`() {
        show(long, pinned = true)
        val top = header("#").fetchSemanticsNode().boundsInRoot.top
        header("H").assertDoesNotExist()
        indexLetter("H").performClick()
        compose.waitForIdle()
        header("H").assertIsDisplayed()
        assertEquals(top, header("H").fetchSemanticsNode().boundsInRoot.top, 2f)
        compose.onNodeWithText("H canção 1").assertIsDisplayed()
        header("#").assertDoesNotExist()
        // and back
        indexLetter("#").performClick()
        compose.waitForIdle()
        assertEquals(top, header("#").fetchSemanticsNode().boundsInRoot.top, 2f)
    }

    @Test fun `a finger sliding along the index takes the list from letter to letter`() {
        show(long, pinned = true)
        val top = header("#").fetchSemanticsNode().boundsInRoot.top
        val from = indexLetter("C").fetchSemanticsNode().boundsInRoot.center
        val to = indexLetter("K").fetchSemanticsNode().boundsInRoot.center
        val root = index().fetchSemanticsNode().boundsInRoot.topLeft
        index().performTouchInput { down(from - root) }
        compose.waitForIdle()
        assertEquals(top, header("C").fetchSemanticsNode().boundsInRoot.top, 2f)
        index().performTouchInput { moveTo(to - root) }
        compose.waitForIdle()
        assertEquals(top, header("K").fetchSemanticsNode().boundsInRoot.top, 2f)
        index().performTouchInput { up() }
    }

    @Test fun `the index hangs over the margin and covers no key`() {
        show(state(letters.flatMap { l -> (1..3).map { "$l canção $it" } }) { i, t -> SongRowUi("s$i", t, "Banda Fictícia", songKey = "C#m", bpm = 100, capo = 2, hasTrack = true) }, pinned = true)
        val letter = compose.onNode(hasAnyAncestor(hasTestTag("letter_index_A")), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val key = compose.onAllNodesWithText("C#m")[0].assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val track = compose.onAllNodesWithContentDescription("Backing track")[0].fetchSemanticsNode().boundsInRoot
        assertTrue("key $key, track mark $track, index letter $letter", key.right <= letter.left && track.right <= letter.left)
    }

    // ---- what the list already did ----
    @Test fun `with the letters in the list, holding a song still selects and select all takes every song`() {
        show(long)
        compose.onNodeWithText("A canção 1").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("1").assertExists()
        compose.onNodeWithContentDescription("Selecionar todas").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("62").assertExists()
        header("A").assertIsDisplayed()
    }

    @Test fun `a tap on a song under a letter opens that song`() {
        var opened = ""
        compose.setContent { StagebookTheme { SongsScreen(long, "", {}, {}, {}, { opened = it }) } }
        compose.onNodeWithText("B canção 1").performClick()
        assertEquals(long.songs.first { it.title == "B canção 1" }.id, opened)
    }
}
