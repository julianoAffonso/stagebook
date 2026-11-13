package br.app.stagebook

import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.BandInbox
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.SetlistCloud
import br.app.stagebook.data.model.Setlist
import br.app.stagebook.data.model.Song
import br.app.stagebook.data.web.HttpRequest
import br.app.stagebook.data.web.WebEditorApi
import br.app.stagebook.data.web.WebEditorAuth
import br.app.stagebook.data.web.WebReceive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The editor on the computer receives a setlist with its breaks, by the 3 words and from the band, placed as the app's
 * own receive screen places them (it created the setlist without them, user 13/11).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class WebEditorReceiveBreaksTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private lateinit var repo: LibraryRepository
    private val device = WebEditorAuth.Device("d", "Chrome · Windows", "h", 0)
    private val titles = listOf("Um", "Dois", "Tres", "Quatro", "Cinco")
    private val list = "STAGEBOOK1\n5\nShow\n" + titles.joinToString("\n")

    @Before fun setUp() {
        repo = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        runBlocking { withTimeout(5_000) { repo.loaded.first { it } } }
    }

    private fun have(vararg which: String) = which.associateWith { repo.saveSong(Song(title = it, text = "[G]la")) }

    private fun call(api: WebEditorApi, path: String, body: String) = api.handle(HttpRequest("POST", path, emptyMap(), body = body.toByteArray()), device)!!.text()

    private fun created(api: WebEditorApi, review: String, name: String = "Show"): Setlist {
        val token = Regex("\"token\":\"([^\"]+)\"").find(review)!!.groupValues[1]
        val made = call(api, "/api/receive/create", """{"token":"$token","name":"$name"}""")
        return repo.library.value.setlist(Regex("\"setlist\":\"([^\"]+)\"").find(made)!!.groupValues[1])!!
    }

    /** By the 3 words: what the tablet fetched, with the breaks the sender told for [list] (null = it told nothing). */
    private fun byWords(breaks: List<Int>?, songs: List<SetlistCloud.SharedSong> = emptyList()): Setlist {
        val told = breaks?.let { mapOf(list to it) }.orEmpty()
        val receiver = WebReceive(repo, { SetlistCloud.Received.Ok(list, songs, breaks = told) })
        val api = WebEditorApi(repo, receiver = { receiver })
        return created(api, call(api, "/api/receive", """{"code":"47-violao-lua"}"""))
    }

    /** From the band: an item waiting in the inbox, opened and created from the computer. */
    private fun fromBand(breaks: List<Int>?, songs: List<SetlistCloud.SharedSong> = emptyList()): Setlist {
        val item = BandInbox.Item("i1", "f", "Celular da Ana", list, songs, breaks = breaks)
        val receiver = WebReceive(repo, { SetlistCloud.Received.NotFound })
        val api = WebEditorApi(repo, receiver = { receiver }, band = { WebEditorApi.Band({ listOf(item) }, {}) })
        return created(api, call(api, "/api/band/open", """{"code":"i1"}"""))
    }

    private fun Setlist.titles() = items.map { repo.song(it)!!.title }

    @Test fun `by the 3 words the setlist is created with its breaks`() {
        have(*titles.toTypedArray())
        val set = byWords(listOf(2, 4))
        assertEquals(titles, set.titles())
        assertEquals(listOf(2, 4), set.breaks)
    }

    @Test fun `from the band the setlist is created with its breaks`() {
        have(*titles.toTypedArray())
        val set = fromBand(listOf(1, 3))
        assertEquals(titles, set.titles())
        assertEquals(listOf(1, 3), set.breaks)
    }

    @Test fun `a song that isn't here hands its break to the next one that came in`() {
        have("Um", "Dois", "Quatro", "Cinco")   // "Tres" is missing: the break before it goes before "Quatro"
        val set = byWords(listOf(2))
        assertEquals(listOf("Um", "Dois", "Quatro", "Cinco"), set.titles())
        assertEquals(listOf(2), set.breaks)
        repo.deleteSetlist(set.id)
        assertEquals(listOf(2), fromBand(listOf(2)).breaks)
    }

    @Test fun `a break left at the start or the end by the missing songs goes away`() {
        have("Dois", "Tres", "Quatro")   // without the first and the last, their breaks have no place
        val set = fromBand(listOf(1, 3, 4))
        assertEquals(listOf("Dois", "Tres", "Quatro"), set.titles())
        assertEquals(listOf(2), set.breaks)
    }

    @Test fun `a song that came along keeps its place and the breaks around it`() {
        have("Um", "Dois", "Quatro", "Cinco")
        val set = byWords(listOf(2, 3), listOf(SetlistCloud.SharedSong("Tres", "", "", "[Am]veio")))
        assertEquals(titles, set.titles())
        assertEquals(listOf(2, 3), set.breaks)
    }

    @Test fun `breaks that make no sense are left out, never taken as they came`() {
        have(*titles.toTypedArray())
        // out of range, repeated, out of order: only the ones this setlist can have, in order
        assertEquals(listOf(1, 3), byWords(listOf(3, 99, 0, -4, 3, 5, 1)).breaks)
        assertEquals(listOf(1, 3), fromBand(listOf(3, 99, 0, -4, 3, 5, 1)).breaks)
    }

    @Test fun `a sender that told nothing about breaks creates a setlist without them`() {
        have(*titles.toTypedArray())
        assertEquals(emptyList<Int>(), byWords(null).breaks)
        assertEquals(emptyList<Int>(), fromBand(null).breaks)
    }

    @Test fun `a sender that told there are none creates a setlist without them`() {
        have(*titles.toTypedArray())
        assertEquals(emptyList<Int>(), byWords(emptyList()).breaks)
    }

    // the page has no "replace": a name already taken makes "Show (2)", and the setlist that was here keeps its own
    @Test fun `with the name taken the new setlist gets the received breaks and the old one keeps its own`() {
        val ids = have(*titles.toTypedArray())
        val old = repo.createSetlist("Show")
        repo.setReceivedSetlist(old, titles.map { ids[it] }, listOf(4))
        val set = byWords(listOf(1, 2))
        assertEquals("Show (2)", set.name)
        assertEquals(listOf(1, 2), set.breaks)
        assertEquals(listOf(4), repo.library.value.setlist(old)!!.breaks)
    }

    // the one way in for the app's receive screen and the page: the same-name replace of the app
    @Test fun `replacing a setlist takes the received breaks, and with none told keeps its own`() {
        val ids = have(*titles.toTypedArray())
        val set = repo.createSetlist("Show")
        val slots = titles.map { ids[it] }
        repo.setReceivedSetlist(set, slots, listOf(4))
        repo.setReceivedSetlist(set, slots, listOf(1, 2))
        assertEquals(listOf(1, 2), repo.library.value.setlist(set)!!.breaks)
        repo.setReceivedSetlist(set, slots.take(4), null)   // an older app, the QR: the break stays before the same song
        assertEquals(listOf(1, 2), repo.library.value.setlist(set)!!.breaks)
        assertEquals(4, repo.library.value.setlist(set)!!.items.size)
    }

    @Test fun `loose songs make no setlist, whatever was told about breaks`() {
        val loose = "STAGEBOOK1\n1\n\nNova"
        val receiver = WebReceive(repo, { SetlistCloud.Received.Ok(loose, listOf(SetlistCloud.SharedSong("Nova", "", "", "[G]la")), breaks = mapOf(loose to listOf(1))) })
        val api = WebEditorApi(repo, receiver = { receiver })
        val review = call(api, "/api/receive", """{"code":"47-violao-lua"}""")
        val token = Regex("\"token\":\"([^\"]+)\"").find(review)!!.groupValues[1]
        assertEquals("""{"setlist":""}""", call(api, "/api/receive/create", """{"token":"$token","name":""}"""))
        assertEquals(0, repo.library.value.sets.size)
    }
}
