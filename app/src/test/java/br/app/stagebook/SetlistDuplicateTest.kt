package br.app.stagebook

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.Song
import br.app.stagebook.ui.setlists.SetItemUi
import br.app.stagebook.ui.setlists.SetlistDetailScreen
import br.app.stagebook.ui.setlists.SetlistDetailUiState
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A copy of a setlist, to build one show from another (user suggestion, 15/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class SetlistDuplicateTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun `the copy has the songs and breaks, a new id and today's day, and comes first`() {
        runBlocking {
            val repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
            withTimeout(5_000) { repo.loaded.first { it } }
            val ids = listOf("A", "B", "C").map { repo.saveSong(Song(title = it, text = "x")) }
            val set = repo.createSetlist("Sábado", date = "2020-01-01").also { repo.addToSetlist(it, ids); repo.addBreak(it) }
            val copy = repo.duplicateSetlist(set, "Sábado (cópia)")!!
            val c = repo.library.value.setlist(copy)!!
            assertNotEquals(set, copy)
            assertEquals(repo.library.value.setlist(set)!!.items, c.items)
            assertEquals(repo.library.value.setlist(set)!!.breaks, c.breaks)
            assertEquals("Sábado (cópia)", c.name)
            assertNotEquals("2020-01-01", c.date)   // today's, not the original's
            assertEquals(copy, repo.library.value.sets.first().id)
            assertNull(repo.duplicateSetlist("nope", "x"))
        }
    }

    @Test fun `the setlist's menu has Duplicate and it asks for the copy`() {
        var asked = 0
        val state = SetlistDetailUiState(loading = false, name = "Show", items = listOf(SetItemUi("a", "a", "A", "", false)))
        compose.setContent { StagebookTheme { SetlistDetailScreen(state, null, {}, {}, {}, {}, {}, { _, _ -> }, onRemove = { _, _ -> }, onDeleteSetlist = {}, onDuplicate = { asked++ }) } }
        compose.onNodeWithContentDescription("Mais opções").performClick()
        compose.onNodeWithText("Duplicar").performClick()
        assertEquals(1, asked)
    }
}
