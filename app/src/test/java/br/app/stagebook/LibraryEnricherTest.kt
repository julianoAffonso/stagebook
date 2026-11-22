package br.app.stagebook

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
        LibraryEnricher(repo, MusicBrainz(), DeezerBpm(), Connectivity(context), scope, br.app.stagebook.data.TrackStore(context), context).apply {
            online = { true }
            deezerSpacingMs = 0
            roundPauseMs = 0
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

    @Test fun `one press is enough - a service that was busy is asked again in the same run`() = runBlocking {
        val repo = repo()
        repo.restore(listOf(Song("a", "Uma", text = Chords.setDirective(Chords.setDirective("x", "tempo", "100"), "duration", "3:00"))), emptyList())
        var calls = 0
        val e = enricher(repo).apply { artistYear = { t, _ -> calls++; if (calls == 1) null else MusicBrainz.Info(t, "Found Artist", "1999") } }
        e.start()
        withTimeout(5_000) { e.progress.first { !it.running && it.done > 0 } }
        assertEquals("Found Artist", repo.song("a")!!.author)
        assertEquals(2, calls)
        assertEquals(0, e.missingCount(repo.library.value.songs))
    }

    @Test fun `what the services don't have leaves the count until the song changes, and Look again asks for it`() = runBlocking {
        val repo = repo()
        repo.restore(listOf(Song("a", "Rara", "Banda", "2000")), emptyList())
        val asked = mutableListOf<String>()
        val e = enricher(repo, asked).apply { facts = { t, _ -> asked += "dz:$t"; null } }
        assertEquals(1, e.missingCount(repo.library.value.songs))
        e.start()
        withTimeout(5_000) { e.progress.first { !it.running && it.done > 0 } }
        assertEquals(0, e.missingCount(repo.library.value.songs))
        assertEquals(1, e.triedCount(repo.library.value.songs))
        // a press now finds nothing to look up; Look again does
        asked.clear()
        e.start()
        assertEquals(emptyList<String>(), asked)
        e.start(again = true)
        withTimeout(5_000) { e.progress.first { !it.running && it.done > 0 } }
        assertTrue(asked.isNotEmpty())
        // a song renamed is another lookup: it counts again
        repo.updateSong("a") { it.copy(title = "Rara (versão)") }
        withTimeout(5_000) { while (repo.song("a")!!.title != "Rara (versão)") kotlinx.coroutines.delay(10) }
        assertEquals(1, e.missingCount(repo.library.value.songs))
    }

    @Test fun `a song with a backing track takes its length from the track`() = runBlocking {
        val repo = repo()
        repo.restore(listOf(Song("a", "Com faixa", "Banda", "2000", track = "t.ogg")), emptyList())
        val e = enricher(repo).apply {
            trackSeconds = { if (it == "t.ogg") 200 else 0 }
            facts = { _, _ -> DeezerBpm.Facts(bpm = 100, seconds = 999) }
        }
        e.start()
        withTimeout(5_000) { e.progress.first { !it.running && it.done > 0 } }
        assertEquals(200, Chords.duration(repo.song("a")!!.text))
        assertEquals(100, Chords.tempo(repo.song("a")!!.text))
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
        compose.onNodeWithText("Buscando a música 1 de 3").performScrollTo().assertExists()
        compose.onNodeWithText("Pode sair desta tela.").assertExists()
        compose.onNodeWithText("Parar").performScrollTo().performClick()
        assertTrue(stopped)
    }
}
