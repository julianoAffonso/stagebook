package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.SetlistShowStore
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.ShowDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

/** A show night is one day until 5 in the morning (user, 14/11): a show past midnight keeps its marks and counters. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ShowDayTest {
    private val sp = ZoneId.of("America/Sao_Paulo")
    private fun day(y: Int, m: Int, d: Int, h: Int, min: Int, zone: ZoneId = sp) =
        ShowDay.of(LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant(), zone)

    @Test fun `23-50 and 00-10 of the same show are one day`() {
        assertEquals(day(2026, 11, 14, 23, 50), day(2026, 11, 15, 0, 10))
        assertEquals("2026-11-14", day(2026, 11, 15, 0, 10))
    }

    @Test fun `04-59 still belongs to the night before and 05-01 is a new day`() {
        assertEquals(day(2026, 11, 14, 22, 0), day(2026, 11, 15, 4, 59))
        assertNotEquals(day(2026, 11, 15, 4, 59), day(2026, 11, 15, 5, 1))
        assertEquals("2026-11-15", day(2026, 11, 15, 5, 1))
    }

    @Test fun `the zone decides the wall clock - the same instant is another day elsewhere`() {
        val instant = LocalDateTime.of(2026, 11, 15, 2, 0).atZone(sp).toInstant()   // 02:00 in São Paulo, 05:00 UTC
        assertEquals("2026-11-14", ShowDay.of(instant, sp))
        assertEquals("2026-11-15", ShowDay.of(instant, ZoneId.of("UTC")))
    }

    @Test fun `a daylight saving change keeps the night together`() {
        val ny = ZoneId.of("America/New_York")   // clocks go forward on 2026-03-08 at 02:00
        assertEquals(day(2026, 3, 7, 22, 0, ny), day(2026, 3, 8, 3, 30, ny))
        assertEquals(day(2026, 3, 7, 22, 0, ny), day(2026, 3, 8, 4, 59, ny))
        assertNotEquals(day(2026, 3, 7, 22, 0, ny), day(2026, 3, 8, 5, 1, ny))
    }

    @Test fun `marks and a break counter survive the stage opening after midnight`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repo = testRepo(scope = scope, empty = true)
        val ids = listOf("A", "B").map { repo.saveSong(Song(title = it, author = "x", text = it)) }
        val set = repo.createSetlist("Show").also { repo.addToSetlist(it, ids); repo.addBreak(it) }
        val store = SetlistShowStore(context, repo, scope)
        val zone = ZoneId.systemDefault()
        fun at(d: Int, h: Int, m: Int) = LocalDateTime.of(2026, 11, d, h, m).atZone(zone).toInstant().toEpochMilli()
        store.now = { at(14, 23, 50) }
        store.stageOpened(set)
        store.onStage(set, ids[0])
        store.breakEntered(set, ids[1])
        store.now = { at(15, 0, 10) }
        store.stageOpened(set)   // the stage opens again after midnight
        assertEquals(ids[0], store.marks.value[set]?.current)
        assertTrue(store.breakOpen(set, ids[1]))
    }
}
