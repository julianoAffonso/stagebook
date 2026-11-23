package br.app.stagebook

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.Backup
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.PlayEntry
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.PlayHistory
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** The history of what was played, exported as a spreadsheet (user, 22/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class PlayHistoryTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun repo() = testRepo()

    @Test fun `a song played counts once per show day, with its setlist and its name as it was`() = runBlocking {
        val repo = repo()
        repo.restore(listOf(Song("s1", "Tempo", "Banda")), emptyList())
        repo.countPlay("s1", "2026-11-20", "Sábado")
        repo.countPlay("s1", "2026-11-20", "Sábado")   // the same day: once
        repo.countPlay("s1", "2026-11-21")
        withTimeout(5_000) { while (repo.library.value.history.size < 2) kotlinx.coroutines.delay(10) }
        assertEquals(listOf(PlayEntry("2026-11-20", "Tempo", "Banda", "Sábado"), PlayEntry("2026-11-21", "Tempo", "Banda", "")), repo.library.value.history)
        assertEquals(2, repo.library.value.song("s1")!!.plays)
    }

    @Test fun `the periods and the size limit`() {
        val today = LocalDate.parse("2026-11-22")
        val h = listOf(PlayEntry("2025-10-01", "Velha"), PlayEntry("2026-01-10", "Ano"), PlayEntry("2026-11-02", "Mês"))
        assertEquals(listOf("Mês"), PlayHistory.of(h, PlayHistory.Period.MONTH, today).map { it.title })
        assertEquals(listOf("Ano", "Mês"), PlayHistory.of(h, PlayHistory.Period.YEAR, today).map { it.title })
        assertEquals(3, PlayHistory.of(h, PlayHistory.Period.ALL, today).size)
        val full = (1..PlayHistory.MAX).map { PlayEntry("2026-01-01", "m$it") }
        assertEquals("m2", PlayHistory.add(full, PlayEntry("2026-01-02", "nova")).first().title)
        assertEquals(PlayHistory.MAX, PlayHistory.add(full, PlayEntry("2026-01-02", "nova")).size)
    }

    @Test fun `the spreadsheet has a header, quotes what needs it and a BOM`() {
        val csv = PlayHistory.csv(listOf(PlayEntry("2026-11-20", "Tempo; Perdido", "Legião \"Urbana\"", "Sábado")), listOf("Data", "Música", "Artista", "Setlist"), ';')
        assertTrue(csv.startsWith("﻿Data;Música;Artista;Setlist\r\n"))
        assertTrue(csv.contains("2026-11-20;\"Tempo; Perdido\";\"Legião \"\"Urbana\"\"\";Sábado\r\n"))
    }

    @Test fun `the history goes in the backup and joins the one here`() = runBlocking {
        val repo = repo()
        repo.restore(listOf(Song("s1", "Tempo")), emptyList())
        repo.countPlay("s1", "2026-11-20", "Sábado")
        withTimeout(5_000) { while (repo.library.value.history.isEmpty()) kotlinx.coroutines.delay(10) }
        val backup = Backup(context, repo, br.app.stagebook.data.TrackStore(context))
        val out = java.io.ByteArrayOutputStream().also { backup.write(it, setOf(Backup.Part.LIBRARY)) }
        repo.joinHistory(emptyList())
        repo.restore(listOf(Song("s1", "Tempo")), emptyList())
        repo.countPlay("s1", "2026-11-21")
        withTimeout(5_000) { while (repo.library.value.history.size < 2) kotlinx.coroutines.delay(10) }
        backup.restore(out.toByteArray().inputStream(), setOf(Backup.Part.LIBRARY))
        assertEquals(listOf("2026-11-20", "2026-11-21"), repo.library.value.history.map { it.day })
    }

    @Test fun `Settings shows how much was played and opens the report`() {
        var opened = 0
        compose.setContent {
            StagebookTheme {
                SettingsScreen(SettingsUiState(loading = false, songCount = 3, playedSongs = 12, playedDays = 2), SettingsActions(onOpenHistory = { opened++ }), Modifier.fillMaxSize(), section = SettingsSection.LIBRARY, onLeave = {}, onMenu = {})
            }
        }
        compose.onNodeWithText("12 músicas tocadas em 2 dias de show.").performScrollTo().assertExists()
        compose.onNodeWithText("Ver o relatório").performScrollTo().performClick()
        assertEquals(1, opened)
    }

    private val played = listOf(
        PlayEntry("2025-03-02", "Antiga", "Banda Velha"),
        PlayEntry("2026-11-07", "Tempo Perdido", "Legião Urbana", "Bar do Zé"),
        PlayEntry("2026-11-21", "Anunciação", "Alceu Valença", "Casamento"),
        PlayEntry("2026-11-21", "Sem Artista"),
    )

    @Test fun `the report lists the period's songs day by day, the latest show first, and exports what it shows`() {
        var period by androidx.compose.runtime.mutableStateOf(PlayHistory.Period.MONTH)
        var exported: PlayHistory.Period? = null
        var left = 0
        compose.setContent {
            StagebookTheme {
                br.app.stagebook.ui.settings.PlayReportScreen(played, period, { period = it }, { exported = it }, { left++ }, today = java.time.LocalDate.parse("2026-11-23"))
            }
        }
        compose.onNodeWithText("3 músicas tocadas em 2 dias de show.").assertExists()
        // each day by its name, the latest above
        val late = compose.onNodeWithText("Sábado, 21 de novembro de 2026").fetchSemanticsNode().positionInRoot.y
        val early = compose.onNodeWithText("Sábado, 7 de novembro de 2026").fetchSemanticsNode().positionInRoot.y
        org.junit.Assert.assertTrue("$late < $early", late < early)
        compose.onNodeWithText("Anunciação").assertExists()
        compose.onNodeWithText("Alceu Valença · Casamento").assertExists()
        compose.onNodeWithText("Sem Artista").assertExists()
        compose.onNodeWithText("Antiga").assertDoesNotExist()   // last year: not this month's
        compose.onNodeWithText("Tudo").performClick()
        // the oldest day is at the end of the list
        compose.onNode(androidx.compose.ui.test.hasScrollToNodeAction()).performScrollToNode(androidx.compose.ui.test.hasText("Antiga"))
        compose.onNodeWithText("Antiga").assertExists()
        compose.onNodeWithText("4 músicas tocadas em 3 dias de show.").assertExists()
        compose.onNodeWithText("Exportar planilha (CSV)").performClick()
        assertEquals(PlayHistory.Period.ALL, exported)
        compose.onNodeWithContentDescription("Voltar").performClick()
        assertEquals(1, left)
    }

    @Test fun `a period with nothing played says so and offers no spreadsheet`() {
        compose.setContent {
            StagebookTheme {
                br.app.stagebook.ui.settings.PlayReportScreen(played.take(1), PlayHistory.Period.MONTH, {}, {}, {}, today = java.time.LocalDate.parse("2026-11-23"))
            }
        }
        compose.onNodeWithText("Nada tocado neste período.").assertExists()
        compose.onNodeWithText("Exportar planilha (CSV)").assertDoesNotExist()
    }

    @Test fun `the days of the report come latest first, each with its songs in the order played`() {
        val days = PlayHistory.byDay(played)
        assertEquals(listOf("2026-11-21", "2026-11-07", "2025-03-02"), days.map { it.first })
        assertEquals(listOf("Anunciação", "Sem Artista"), days.first().second.map { it.title })
    }
}
