package br.app.stagebook

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.Connectivity
import br.app.stagebook.data.DeezerBpm
import br.app.stagebook.data.LibraryEnricher
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.MusicBrainz
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.Chords
import br.app.stagebook.ui.settings.SettingsActions
import br.app.stagebook.ui.settings.SettingsScreen
import br.app.stagebook.ui.settings.SettingsSection
import br.app.stagebook.ui.settings.SettingsUiState
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

/** Completing what the whole library lacks, at each service's pace (user, 22/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class LibraryEnricherTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun repo() = LibraryRepository(context, scope).also { r -> runBlocking { withTimeout(5_000) { r.loaded.first { it } } } }

    private fun enricher(repo: LibraryRepository, asked: MutableList<String> = mutableListOf()) =
        LibraryEnricher(repo, MusicBrainz(), DeezerBpm(), Connectivity(context), scope).apply {
            online = { true }
            deezerSpacingMs = 0
            artistYear = { t, _ -> asked += "mb:$t"; MusicBrainz.Info(t, "Found Artist", "1999") }
            facts = { t, _ -> asked += "dz:$t"; DeezerBpm.Facts(bpm = 120, seconds = 225) }
        }

    @Test fun `only empty fields are filled`() {
        val written = Song("a", "Tempo", "Banda", "2001", text = Chords.setDirective("x", "tempo", "90"))
        val got = LibraryEnricher.fill(written, MusicBrainz.Info("Tempo", "Outra", "1980"), DeezerBpm.Facts(140, 200))
        assertEquals("Banda", got.author)
        assertEquals("2001", got.year)
        assertEquals(90, Chords.tempo(got.text))
        assertEquals(200, Chords.duration(got.text))
    }

    @Test fun `the whole library is looked up, songs already complete are left alone`() = runBlocking {
        val repo = repo()
        val complete = Song("c", "Pronta", "Banda", "2000", text = Chords.setDirective(Chords.setDirective("x", "tempo", "100"), "duration", "3:00"))
        repo.restore(listOf(Song("a", "Uma"), Song("b", "Duas", "Banda", "2010"), complete, Song("d", "")), emptyList())
        val asked = mutableListOf<String>()
        val e = enricher(repo, asked)
        assertEquals(2, e.missingCount(repo.library.value.songs))
        e.start()
        withTimeout(5_000) { e.progress.first { !it.running && it.done == 2 } }
        assertEquals(2, e.progress.value.filled)
        val a = repo.song("a")!!
        assertEquals("Found Artist", a.author)
        assertEquals("1999", a.year)
        assertEquals(120, Chords.tempo(a.text))
        assertEquals(225, Chords.duration(a.text))
        // "Duas" had artist and year: MusicBrainz isn't asked for it, only Deezer
        assertEquals(listOf("mb:Uma", "dz:Uma", "dz:Duas"), asked)
        assertEquals(complete, repo.song("c"))
    }

    @Test fun `offline or in essential mode nothing is asked`() = runBlocking {
        val repo = repo()
        repo.restore(listOf(Song("a", "Uma")), emptyList())
        val asked = mutableListOf<String>()
        val e = enricher(repo, asked).apply { online = { false } }
        e.start()
        assertTrue(e.progress.value.offline)
        repo.updateLook { it.copy(internetOn = false) }
        withTimeout(5_000) { while (repo.library.value.look.internetOn) kotlinx.coroutines.delay(10) }
        e.online = { true }
        e.start()
        assertFalse(e.progress.value.running)
        assertEquals(emptyList<String>(), asked)
    }

    @Test fun `Settings shows what is missing, the progress and stops`() {
        var started = false
        var stopped = false
        var state by mutableStateOf(SettingsUiState(loading = false, songCount = 5, missingData = 3))
        compose.setContent {
            StagebookTheme {
                SettingsScreen(state, SettingsActions(onEnrich = { started = true }, onStopEnrich = { stopped = true }), Modifier.fillMaxSize(), section = SettingsSection.LIBRARY, onLeave = {}, onMenu = {})
            }
        }
        compose.onNodeWithText("3 músicas sem artista, ano, BPM ou duração. Só os campos vazios são preenchidos.").performScrollTo().assertExists()
        compose.onNodeWithText("Buscar o que falta").performScrollTo().performClick()
        assertTrue(started)
        state = state.copy(enrich = LibraryEnricher.Progress(running = true, done = 1, total = 3))
        compose.onNodeWithText("Buscando a música 1 de 3. Pode sair desta tela.").performScrollTo().assertExists()
        compose.onNodeWithText("Parar").performScrollTo().performClick()
        assertTrue(stopped)
    }
}
