package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Splitting national and international: national is the app's language, whatever it is (user, 01/11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AutoTagsTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val pt = "Eu quero você comigo\nQue a vida é tão bonita quando você está aqui"
    private val en = "You know I love you, baby\nAnd I can't let it go, it's all in my heart"

    private fun repo(): Pair<LibraryRepository, List<String>> = runBlocking {
        val r = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        withTimeout(5_000) { r.loaded.first { it } }
        r to listOf(r.saveSong(Song(title = "Brasileira", text = pt)), r.saveSong(Song(title = "Gringa", text = en)))
    }

    @Test fun `app in Portuguese - the Portuguese song is national`() {
        val (r, ids) = repo()
        assertEquals(1 to 1, r.autoTags("Nacional", "Internacional", "pt", preview = false))
        assertEquals(listOf("Nacional"), r.song(ids[0])!!.tags)
        assertEquals(listOf("Internacional"), r.song(ids[1])!!.tags)
    }

    @Test fun `app in English - the English song is local`() {
        val (r, ids) = repo()
        r.autoTags("Local", "International", "en", preview = false)
        assertEquals(listOf("International"), r.song(ids[0])!!.tags)
        assertEquals(listOf("Local"), r.song(ids[1])!!.tags)
    }

    @Test fun `a language with no word list tags nothing`() {
        val (r, _) = repo()
        assertEquals(0 to 0, r.autoTags("Nacional", "Internacional", "ja", preview = false))
    }
}
