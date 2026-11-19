package br.app.stagebook

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.Setlist
import br.app.stagebook.ui.setlists.SetlistEditDialog
import br.app.stagebook.ui.setlists.SetlistRowUi
import br.app.stagebook.ui.setlists.SetlistsScreen
import br.app.stagebook.ui.setlists.SetlistsUiState
import br.app.stagebook.ui.setlists.monthOf
import br.app.stagebook.ui.setlists.setlistMonths
import br.app.stagebook.ui.setlists.setlistOrder
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The setlist's show day: stored, ordered, grouped by month, and asked for in the window (user, 15/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class SetlistDateTest {
    @get:Rule val compose = createComposeRule()
    private val scopes = mutableListOf<CoroutineScope>()
    @After fun stop() { scopes.forEach { it.cancel() }; scopes.clear() }

    private fun repo(): LibraryRepository {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
        return LibraryRepository(ApplicationProvider.getApplicationContext<android.app.Application>(), scope)
            .also { r -> runBlocking { withTimeout(5_000) { r.loaded.first { it } }; r.restore(emptyList(), emptyList()) } }
    }

    private fun row(id: String, date: String) = SetlistRowUi(id, id, 0, date)

    @Test fun `a new setlist takes today as its day`() {
        val r = repo()
        val id = r.createSetlist("Show")
        assertEquals(Setlist.today(), r.library.value.setlist(id)?.date)
    }

    @Test fun `a day given when creating is kept`() {
        val r = repo()
        val id = r.createSetlist("Show", "2026-12-24")
        assertEquals("2026-12-24", r.library.value.setlist(id)?.date)
    }

    @Test fun `the day can be changed and taken off and a text that is no date is refused`() {
        val r = repo()
        val id = r.createSetlist("Show", "2026-12-24")
        r.setSetlistDate(id, "2027-01-02")
        assertEquals("2027-01-02", r.library.value.setlist(id)?.date)
        r.setSetlistDate(id, "ontem")
        assertEquals("2027-01-02", r.library.value.setlist(id)?.date)
        r.setSetlistDate(id, "")
        assertEquals("", r.library.value.setlist(id)?.date)
    }

    @Test fun `a setlist saved before the day existed reads with no day`() {
        val old = Json { ignoreUnknownKeys = true }.decodeFromString<Setlist>("""{"id":"1","name":"Antigo","items":["a"]}""")
        assertEquals("", old.date)
    }

    @Test fun `the newest day comes first and the ones with no day go last`() {
        val sets = listOf(row("sem", ""), row("velho", "2025-01-17"), row("novo", "2026-11-15"), row("meio", "2025-09-26"))
        assertEquals(listOf("novo", "meio", "velho", "sem"), setlistOrder(sets).map { it.id })
    }

    @Test fun `two setlists on the same day keep the stored order`() {
        val sets = listOf(row("b", "2026-11-15"), row("a", "2026-11-15"))
        assertEquals(listOf("b", "a"), setlistOrder(sets).map { it.id })
    }

    @Test fun `the groups follow the order by month and the ones with no day are their own group at the end`() {
        val ordered = setlistOrder(listOf(row("sem", ""), row("a", "2025-09-07"), row("b", "2025-09-26"), row("c", "2026-11-15")))
        val groups = setlistMonths(ordered)
        assertEquals(listOf("2026-11", "2025-09", ""), groups.map { it.first })
        assertEquals(listOf("b", "a"), groups[1].second.map { it.id })
        assertEquals("2025-09", monthOf("2025-09-26"))
        assertEquals("", monthOf(""))
    }

    @Test fun `the window shows no date for a setlist without one and confirms what was typed`() {
        var got: Pair<String, String>? = null
        compose.setContent { StagebookTheme { SetlistEditDialog("Novo setlist", "Show", "", "Nome", { n, d -> got = n to d }, {}) } }
        compose.onNodeWithText("Sem data").assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        assertEquals("Show" to "", got)
    }

    @Test fun `the window shows the day and takes it off`() {
        var got: Pair<String, String>? = null
        compose.setContent { StagebookTheme { SetlistEditDialog("Editar setlist", "Show", "2026-12-24", "Nome", { n, d -> got = n to d }, {}) } }
        compose.onNodeWithText("24 de dez. de 2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tirar a data").performClick()
        compose.onNodeWithText("Sem data").assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        assertEquals("Show" to "", got)
    }
}
