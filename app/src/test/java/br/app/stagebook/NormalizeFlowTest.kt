package br.app.stagebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.Song
import br.app.stagebook.ui.settings.SettingsActions
import br.app.stagebook.ui.settings.SettingsMessage
import br.app.stagebook.ui.settings.SettingsScreen
import br.app.stagebook.ui.settings.SettingsSection
import br.app.stagebook.ui.settings.SettingsUiState
import br.app.stagebook.ui.settings.SettingsViewModel
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Padronizar seções da biblioteca": do botão até a letra gravada. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class NormalizeFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `button and confirmation call mark sections`() {
        var calls = 0
        compose.setContent {
            StagebookTheme { SettingsScreen(SettingsUiState(loading = false), SettingsActions(onNormalize = { calls++ }), section = SettingsSection.SECTIONS) }
        }
        compose.onNodeWithText("Marcar seções nas letras sem títulos").performScrollTo().performClick()
        compose.onNodeWithText("Marcar").performClick()
        compose.waitForIdle()
        assertEquals(1, calls)
    }

    @Test
    fun `mark sections only touches lyrics without titles and says how many changed`() = runBlocking {
        Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher())   // o trabalho saiu da thread principal
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = LibraryRepository(context, CoroutineScope(Dispatchers.IO))
        withTimeout(5_000) { repo.loaded.first { it } }
        repo.saveSong(Song(title = "A", text = "Chorus:\nlinha\n\nVerse 2\noutra"))
        repo.saveSong(Song(title = "B", text = "[Refrão]\nsem mudança"))
        repo.saveSong(Song(title = "E", text = "{start_of_chorus}\num\n{end_of_chorus}\n\num\n\ndois\n\num"))
        repo.saveSong(Song(title = "C", text = "um\ndois\n\nrefrão aqui\nde novo\n\ntrês\nquatro\n\nrefrão aqui\nde novo"))
        repo.saveSong(Song(title = "D", text = "nada\nse\n\nrepete\naqui"))
        val vm = SettingsViewModel(repo, context, br.app.stagebook.data.AppUpdater(context), br.app.stagebook.data.MidiPedal(context, repo, CoroutineScope(Dispatchers.IO)), br.app.stagebook.data.Backup(context, repo, br.app.stagebook.data.TrackStore(context)))
        vm.normalizeSections()
        assertEquals(SettingsMessage.Normalized(1, 1), withTimeout(5_000) { vm.messages.first() })
        // títulos que já existem ficam como estão (o palco mostra na língua do app)
        assertEquals("Chorus:\nlinha\n\nVerse 2\noutra", repo.findSong("A", "")!!.text)
        assertEquals("{start_of_chorus}\num\n{end_of_chorus}\n\num\n\ndois\n\num", repo.findSong("E", "")!!.text)
        Dispatchers.resetMain()
        assertEquals("[Verso 1]\num\ndois\n\n[Refrão]\nrefrão aqui\nde novo\n\n[Verso 2]\ntrês\nquatro\n\n[Refrão]\nrefrão aqui\nde novo", repo.findSong("C", "")!!.text)
    }
}
