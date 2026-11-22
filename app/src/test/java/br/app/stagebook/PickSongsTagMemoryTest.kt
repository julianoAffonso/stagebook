package br.app.stagebook

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.Song
import br.app.stagebook.ui.setlists.PickSongsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The tag filter of "Add songs" comes back as it was left, for that setlist (user, 14/11). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PickSongsTagMemoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun `the tags chosen while adding songs come back for that setlist only`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            runBlocking {
                val repo = testRepo()
                repo.saveSong(Song(title = "Um", author = "x", text = "linha", tags = listOf("Rock")))
                repo.saveSong(Song(title = "Dois", author = "x", text = "linha", tags = listOf("Lenta")))
                val a = repo.createSetlist("A")
                val b = repo.createSetlist("B")
                fun vm(set: String) = PickSongsViewModel(repo, SavedStateHandle(mapOf("setId" to set)))
                val first = vm(a)
                val job = CoroutineScope(Dispatchers.Main).launch { first.uiState.collect {} }
                withTimeout(5_000) { first.uiState.first { it.rows.size == 2 } }
                first.onTagsChange(setOf("Rock"))
                withTimeout(5_000) { first.uiState.first { s -> s.rows.map { it.title } == listOf("Um") } }
                job.cancel()

                // the screen opens again on the same setlist: the filter is still Rock
                val again = vm(a)
                val job2 = CoroutineScope(Dispatchers.Main).launch { again.uiState.collect {} }
                val s = withTimeout(5_000) { again.uiState.first { it.rows.isNotEmpty() } }
                assertEquals(setOf("Rock"), s.chosenTags)
                assertEquals(listOf("Um"), s.rows.map { it.title })
                job2.cancel()

                // another setlist starts with no filter
                val other = vm(b)
                val job3 = CoroutineScope(Dispatchers.Main).launch { other.uiState.collect {} }
                val o = withTimeout(5_000) { other.uiState.first { it.rows.isNotEmpty() } }
                assertEquals(emptySet<String>(), o.chosenTags)
                assertEquals(2, o.rows.size)
                job3.cancel()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
