package br.app.stagebook

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.web.HttpRequest
import br.app.stagebook.data.web.WebEditorApi
import br.app.stagebook.data.web.WebEditorAuth
import br.app.stagebook.domain.Chords
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A song's length (`{duration}`) and the length of each set (user, 22/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class SongDurationTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun `lengths read as m-ss, h-mm-ss or seconds, and nonsense is none`() {
        assertEquals(225, Chords.parseDuration("3:45"))
        assertEquals(3750, Chords.parseDuration("1:02:30"))
        assertEquals(225, Chords.parseDuration("225"))
        listOf("", "3:75", "abc", "3::4", "99:99:99").forEach { assertEquals(it, 0, Chords.parseDuration(it)) }
        assertEquals("3:45", Chords.formatDuration(225))
        assertEquals("1:02:30", Chords.formatDuration(3750))
        assertEquals("3:45", Chords.durationFromDigits("345"))
        assertEquals("0:45", Chords.durationFromDigits("45"))
        assertEquals("12:34", Chords.durationFromDigits("1234"))
        assertEquals("", Chords.durationFromDigits(""))
    }

    @Test fun `the length lives in the details, out of the lyrics, and comes back when they are saved`() {
        val text = "{duration: 3:45}\n{tempo: 90}\n[Verso]\nLinha"
        assertEquals(225, Chords.meta(text).duration)
        assertFalse(Chords.hideMeta(text).contains("duration"))
        assertEquals(225, Chords.duration(Chords.withMeta("[Verso]\nLinha nova", text)))
    }

    @Test fun `each set shows its length, and songs with none are said apart`() {
        val set = SetlistDetailUiState(
            loading = false, name = "Sábado",
            items = listOf(
                SetItemUi("a", "1", "Um", "", false, seconds = 200),
                SetItemUi("b", "2", "Dois", "", false, seconds = 220),
                SetItemUi("c", "3", "Três", "", false, seconds = 0),
                SetItemUi("d", "4", "Quatro", "", false, seconds = 1500),
            ),
            breaks = listOf(3),
        )
        compose.setContent { StagebookTheme { SetlistDetailScreen(set, null, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}) } }
        compose.onNodeWithText("BLOCO 1 · 3 músicas · 7 min + 1 sem duração").assertExists()
        compose.onNodeWithText("BLOCO 2 · 1 música · 25 min").assertExists()
    }

    @Test fun `the editor on the computer keeps and changes the length`() {
        val repo = testRepo()
        val api = WebEditorApi(repo)
        val device = WebEditorAuth.Device("d", "n", "h", 0)
        api.handle(HttpRequest("POST", "/api/songs", body = """{"title":"Tempo","text":"[Verso]\nPrimeira linha da letra\nSegunda linha","duration":"345"}""".toByteArray()), device)
        val song = repo.findSong("Tempo", "")!!
        assertEquals(225, Chords.duration(song.text))
        // saving again from the page without touching the length keeps it
        api.handle(HttpRequest("PUT", "/api/songs/${song.id}", body = """{"title":"Tempo","text":"[Verso]\nPrimeira linha da letra\nOutra linha","duration":"3:45"}""".toByteArray()), device)
        assertEquals(225, Chords.duration(repo.findSong("Tempo", "")!!.text))
        assertTrue(String(api.handle(HttpRequest("GET", "/api/songs/${song.id}"), device)!!.body).contains("\"duration\":\"3:45\""))
    }
}
