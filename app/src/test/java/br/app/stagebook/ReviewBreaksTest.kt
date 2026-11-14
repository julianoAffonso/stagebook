package br.app.stagebook

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import br.app.stagebook.data.BandInbox
import br.app.stagebook.data.BandLink
import br.app.stagebook.data.DeviceIdentity
import br.app.stagebook.data.ImportQueue
import br.app.stagebook.data.LibraryRepository
import br.app.stagebook.data.SetlistCloud
import br.app.stagebook.data.model.Song
import br.app.stagebook.domain.QrPayload
import br.app.stagebook.ui.setlists.ScanRow
import br.app.stagebook.ui.setlists.ScanSetlistScreen
import br.app.stagebook.ui.setlists.ScanSetlistViewModel
import br.app.stagebook.ui.setlists.ScanUi
import br.app.stagebook.ui.setlists.SongRef
import br.app.stagebook.ui.theme.StagebookTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The review of what arrived says the breaks that came with the setlist: before, they only showed after creating. The
 * number is the one the setlist will have with the songs that go in, never just what the sender said.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pt-rBR", application = android.app.Application::class)
class ReviewBreaksTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val identity = DeviceIdentity(context)

    private fun repo() = LibraryRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        .also { r -> runBlocking { withTimeout(5_000) { r.loaded.first { it } }; r.restore(emptyList(), emptyList()) } }

    private fun LibraryRepository.songs(vararg titles: String) = titles.map { saveSong(Song(title = it, author = "x", text = it)) }

    private fun scan(repo: LibraryRepository, inbox: BandInbox = BandInbox(context), from: String = ""): ScanSetlistViewModel {
        val link = BandLink(context, identity, br.app.stagebook.data.BandTrust(context), br.app.stagebook.data.StageGate(), inbox, repo)
        val cloud = SetlistCloud(repo, identity)
        val internet = br.app.stagebook.data.BandInternet(
            context, cloud, br.app.stagebook.data.BandTrust(context), identity, inbox, br.app.stagebook.data.Connectivity(context), link,
        )
        return ScanSetlistViewModel(
            repo, ImportQueue(repo), cloud, androidx.lifecycle.SavedStateHandle(mapOf("code" to "", "inbox" to from)), inbox, link, internet,
            br.app.stagebook.data.BandTrust(context), br.app.stagebook.data.Connectivity(context),
        )
    }

    private fun list(name: String, vararg titles: String) = (listOf(QrPayload.HEADER, titles.size.toString(), name) + titles).joinToString("\n")
    private fun ScanSetlistViewModel.review() = ui.value as ScanUi.Review
    private fun LibraryRepository.breaksOf(id: String) = library.value.setlist(id)!!.breaks

    @Test fun `from the band's inbox the review counts the breaks the setlist will have`() {
        val repo = repo()
        repo.songs("A", "B", "C", "D")
        val inbox = BandInbox(context)
        inbox.add("ana", "Ana", list("Da banda", "A", "B", "C", "D"), emptyList(), listOf(1, 3))
        val vm = scan(repo, inbox, inbox.items.value.single { it.setName == "Da banda" }.id)
        assertEquals(2, vm.review().breakCount)
        assertEquals(2, repo.breaksOf(vm.create()!!).size)
    }

    @Test fun `by the 3 words the count follows the songs that really go in`() {
        val sender = repo()
        val cloud = SetlistCloud(sender, identity)
        val show = sender.createSetlist("Show")
        sender.addToSetlist(show, sender.songs("A", "B", "C", "D", "E"))
        sender.addBreak(show); sender.addBreak(show); sender.moveRow(show, 3, 2)   // A B | C D | E
        val got = cloud.read(listOf(cloud.setlistPackage(show, withSongs = false)!!.first)) as SetlistCloud.Received.Ok

        // this device lacks C, D and E: the two breaks have no song after them
        val repo = repo()
        val ids = repo.songs("A", "B", "Dê")
        val vm = scan(repo)
        vm.review(got.text, got.songs, got.breaks[got.text])
        assertEquals(listOf(2, 4), vm.review().breaks)
        assertEquals(0, vm.review().breakCount)
        // D picked by hand: A B | D, one break; the one before E still has nothing after it
        vm.choose(3, ids[2])
        assertEquals(1, vm.review().breakCount)
        assertEquals(listOf(2), repo.breaksOf(vm.create()!!))
    }

    @Test fun `a song that comes along counts as in, and two breaks that meet count once`() {
        val repo = repo()
        repo.songs("A", "B", "E")
        val vm = scan(repo)
        // A B | C | D E, C and D missing: both breaks end up before E
        vm.review(list("Show", "A", "B", "C", "D", "E"), emptyList(), listOf(2, 3))
        assertEquals(1, vm.review().breakCount)
        // with C sent along, each break keeps its place
        vm.review(list("Show", "A", "B", "C", "D", "E"), listOf(SetlistCloud.SharedSong("C", text = "la")), listOf(2, 3))
        assertEquals(2, vm.review().breakCount)
        assertEquals(listOf(2, 3), repo.breaksOf(vm.create()!!))
    }

    @Test fun `no breaks sent, a QR or loose songs - nothing to count`() {
        val repo = repo()
        repo.songs("A", "B", "C")
        val vm = scan(repo)
        vm.review(list("Show", "A", "B", "C"), emptyList())            // an older app says nothing
        assertNull(vm.review().breaks)
        assertEquals(0, vm.review().breakCount)
        vm.review(list("Show", "A", "B", "C"), emptyList(), emptyList())   // it said: no breaks
        assertEquals(0, vm.review().breakCount)
        vm.review(list("", "A", "B", "C"), emptyList(), listOf(1))      // loose songs make no setlist
        assertEquals(0, vm.review().breakCount)
        val qr = scan(repo)
        qr.onText(list("Show", "A", "B", "C"))
        assertNull(qr.review().breaks)
        assertEquals(0, qr.review().breakCount)
    }

    // ---- what the screen writes ----

    private fun row(id: String, here: Boolean = true) = ScanRow(id, "", here, if (here) listOf(SongRef(id, id, "")) else emptyList(), id.takeIf { here })

    private fun show(review: ScanUi.Review) = compose.setContent {
        StagebookTheme { ScanSetlistScreen(review, true, {}, {}, {}, { _, _ -> }, {}, { null }, {}, {}, canScanAgain = false) }
    }

    @Test fun `the review line says the breaks after the songs`() {
        show(ScanUi.Review("Show", listOf(row("A"), row("B"), row("C"), row("D"), row("E")), breaks = listOf(2, 4)))
        compose.onNodeWithText("5 de 5 músicas entram no setlist · 2 intervalos").assertExists()
    }

    @Test fun `the line counts only the breaks that stay`() {
        // A B | C D | E without E: one break left
        show(ScanUi.Review("Show", listOf(row("A"), row("B"), row("C"), row("D"), row("E", here = false)), breaks = listOf(2, 4)))
        compose.onNodeWithText("4 de 5 músicas entram no setlist · 1 intervalo").assertExists()
    }

    @Test fun `without breaks the line says only the songs`() {
        show(ScanUi.Review("Show", listOf(row("A"), row("B"))))
        compose.onNodeWithText("2 de 2 músicas entram no setlist").assertExists()
        compose.onAllNodesWithText("intervalo", substring = true).assertCountEquals(0)
    }
}
