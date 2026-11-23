package br.app.stagebook

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LastStage
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.Library
import br.app.stagebook.data.model.Setlist
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.StageStart
import br.app.stagebook.navigation.Stage
import br.app.stagebook.navigation.lastStageRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The app opens on the stage that was open last (user, 15/11): what is kept, and what it turns into at launch. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class LastStageTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val scope = testScope()

    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun reset() { Dispatchers.resetMain(); scope.cancel() }

    private fun repo() = testRepo(scope = scope, empty = true)

    private val library = Library(
        songs = listOf(Song(id = "s1", title = "A"), Song(id = "s2", title = "B")),
        sets = listOf(Setlist(id = "L1", name = "Show", items = listOf("s1", "s2")), Setlist(id = "L0", name = "Vazio")),
    )
    private val at = StageStart(1, true)

    @Test fun `nothing kept gives nothing to open on`() {
        context.getSharedPreferences("last_stage", 0).edit().clear().commit()
        assertNull(LastStage(context).place())
        assertNull(lastStageRoute(null, library) { at })
    }

    @Test fun `a setlist is kept and a song opened alone replaces it`() {
        val last = LastStage(context)
        last.record("L1", "s2")
        assertEquals(LastStage.Place("L1", null), last.place())
        last.record(null, "s1")
        assertEquals(LastStage.Place(null, "s1"), last.place())
    }

    @Test fun `a setlist opens where the show stopped`() {
        assertEquals(Stage(setId = "L1", start = 1, atBreak = true), lastStageRoute(LastStage.Place("L1", null), library) { at })
    }

    @Test fun `a song opened alone opens alone`() {
        assertEquals(Stage(songId = "s1"), lastStageRoute(LastStage.Place(null, "s1"), library) { at })
    }

    @Test fun `what was deleted or is empty gives nothing to open on`() {
        assertNull(lastStageRoute(LastStage.Place("gone", null), library) { at })
        assertNull(lastStageRoute(LastStage.Place("L0", null), library) { at })
        assertNull(lastStageRoute(LastStage.Place(null, "gone"), library) { at })
    }

    @Test fun `the stage keeps itself when a song is shown`() {
        val r = repo()
        val s = r.saveSong(Song(title = "Sozinha", text = "linha"))
        val last = LastStage(context)
        context.getSharedPreferences("last_stage", 0).edit().clear().commit()
        testStage(context, r, SavedStateHandle(mapOf("songId" to s)), lastStage = last).songShown(0)
        assertEquals(LastStage.Place(null, s), last.place())
    }

    @Test fun `the stage of a setlist keeps the setlist`() {
        val r = repo()
        val s = r.saveSong(Song(title = "Sozinha", text = "linha"))
        val set = r.createSetlist("Show").also { r.addToSetlist(it, listOf(s)) }
        val last = LastStage(context)
        testStage(context, r, SavedStateHandle(mapOf("setId" to set, "start" to 0)), lastStage = last).songShown(0)
        assertEquals(LastStage.Place(set, null), last.place())
    }

    @Test fun `the leader break alone is not kept`() {
        val r = repo()
        val last = LastStage(context)
        last.record(null, "before")
        testStage(context, r, SavedStateHandle(mapOf("setId" to "x", "leaderBreak" to true)), lastStage = last).songShown(0)
        assertEquals(LastStage.Place(null, "before"), last.place())
    }
}
